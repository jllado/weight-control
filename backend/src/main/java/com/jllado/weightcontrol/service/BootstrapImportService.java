package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BootstrapImportService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapImportService.class);
    private static final int DAILY_STATUS_BATCH_DAYS = 120;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final HabitRepository habitRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final PhotoStorageService photoStorageService;
    private final TransactionTemplate transactionTemplate;

    public BootstrapImportService(
        AppProperties properties,
        ObjectMapper objectMapper,
        UserRepository userRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        HabitRepository habitRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository,
        DailyStatusRepository dailyStatusRepository,
        PhotoStorageService photoStorageService,
        PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.habitRepository = habitRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.photoStorageService = photoStorageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void importIfNeeded() throws IOException {
        boolean enabled = properties.importData().enabled();
        long userCount = userRepository.count();
        boolean backupExists = Files.exists(properties.importData().backupHtmlPath());
        log.info("Import check enabled={} userCount={} backupExists={} backupPath={}", enabled, userCount, backupExists, properties.importData().backupHtmlPath());
        if (!enabled || userCount > 0 || !backupExists) {
            return;
        }

        Map<String, String> sections = extractSections(Files.readString(properties.importData().backupHtmlPath()));
        List<WeightImportRow> weights = objectMapper.readValue(sections.get("WEIGHTS"), new TypeReference<>() {});
        List<BloodPressureImportRow> bloodPressures = objectMapper.readValue(sections.get("BLOOD PRESSURES"), new TypeReference<>() {});
        List<HabitImportRow> habits = objectMapper.readValue(sections.get("HABITS"), new TypeReference<>() {});
        List<RoutineImportRow> routines = objectMapper.readValue(sections.get("ROUTINES"), new TypeReference<>() {});
        Map<String, PhotoMappingRow> photoMapping = loadPhotoMapping();

        Set<String> userEmails = new LinkedHashSet<>();
        weights.forEach(row -> userEmails.add(row.user()));
        bloodPressures.forEach(row -> userEmails.add(row.user()));
        habits.forEach(row -> userEmails.add(row.user()));
        routines.forEach(row -> userEmails.add(row.user()));

        log.info(
            "Starting bootstrap import users={} weights={} bloodPressures={} habits={} routines={} photos={}",
            userEmails.size(),
            weights.size(),
            bloodPressures.size(),
            habits.size(),
            routines.size(),
            photoMapping.size()
        );

        for (String userEmail : userEmails) {
            importUser(userEmail, weights, bloodPressures, habits, routines, photoMapping);
        }

        log.info("Bootstrap import completed for {} users", userEmails.size());
    }

    private void importUser(
        String email,
        List<WeightImportRow> weights,
        List<BloodPressureImportRow> bloodPressures,
        List<HabitImportRow> habits,
        List<RoutineImportRow> routines,
        Map<String, PhotoMappingRow> photoMapping
    ) throws IOException {
        List<WeightImportRow> userWeights = weights.stream()
            .filter(item -> email.equals(item.user()))
            .sorted(Comparator.comparing(WeightImportRow::date))
            .toList();
        List<BloodPressureImportRow> userBloodPressures = bloodPressures.stream()
            .filter(item -> email.equals(item.user()))
            .sorted(Comparator.comparing(BloodPressureImportRow::date))
            .toList();
        List<HabitImportRow> userHabits = habits.stream()
            .filter(item -> email.equals(item.user()))
            .sorted(Comparator.comparing(HabitImportRow::startDate))
            .toList();
        List<RoutineImportRow> userRoutines = routines.stream()
            .filter(item -> email.equals(item.user()))
            .sorted(Comparator.comparing(RoutineImportRow::startDate))
            .toList();

        log.info(
            "Importing user={} weights={} bloodPressures={} habits={} routines={}",
            email,
            userWeights.size(),
            userBloodPressures.size(),
            userHabits.size(),
            userRoutines.size()
        );

        ImportContext context;
        try {
            context = transactionTemplate.execute(status -> importUserData(email, userWeights, userBloodPressures, userHabits, userRoutines, photoMapping));
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }

        if (context == null) {
            throw new IllegalStateException("Import context was not created");
        }

        backfillDailyStatuses(context);
        log.info("Finished user={} dailyStatuses={}", email, ChronoUnit.DAYS.between(context.firstStatusDate(), context.anchorDate()) + 1);
    }

    private ImportContext importUserData(
        String email,
        List<WeightImportRow> userWeights,
        List<BloodPressureImportRow> userBloodPressures,
        List<HabitImportRow> userHabits,
        List<RoutineImportRow> userRoutines,
        Map<String, PhotoMappingRow> photoMapping
    ) {
        try {
            User user = new User();
            user.setEmail(email);
            user.setDisplayName(email);
            userRepository.save(user);

            Map<String, Long> importedWeightIds = new HashMap<>();
            NavigableMap<LocalDate, Long> weightIdsByDate = new TreeMap<>();
            Weight previousWeight = null;
            for (WeightImportRow row : userWeights) {
                Weight weight = new Weight();
                weight.setLegacyFirebaseId(row.id());
                weight.setUser(user);
                weight.setMeasuredAt(toOffsetDateTime(row.date()));
                weight.setWeight(Numbers.round(row.weight()));
                weight.setFatPercentage(Numbers.round(row.fatPercentage()));
                weight.setFat(Numbers.round(weight.getFatPercentage().multiply(weight.getWeight()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)));
                weight.setMuscle(Numbers.round(row.muscle()));
                weight.setMusclePercentage(Numbers.round(weight.getMuscle().multiply(BigDecimal.valueOf(100)).divide(weight.getWeight(), 2, RoundingMode.HALF_UP)));
                applyWeightDeltas(weight, previousWeight);
                weightRepository.save(weight);
                previousWeight = weight;
                importedWeightIds.put(row.id(), weight.getId());
                weightIdsByDate.put(DateTimes.toLocalDate(weight.getMeasuredAt()), weight.getId());
            }

            NavigableMap<LocalDate, Long> bloodPressureIdsByDate = new TreeMap<>();
            BloodPressure previousBloodPressure = null;
            for (BloodPressureImportRow row : userBloodPressures) {
                BloodPressure bloodPressure = new BloodPressure();
                bloodPressure.setLegacyFirebaseId(row.id());
                bloodPressure.setUser(user);
                bloodPressure.setMeasuredAt(toOffsetDateTime(row.date()));
                bloodPressure.setUpper(row.upper());
                bloodPressure.setLower(row.lower());
                applyBloodPressureDeltas(bloodPressure, previousBloodPressure);
                bloodPressureRepository.save(bloodPressure);
                previousBloodPressure = bloodPressure;
                bloodPressureIdsByDate.put(DateTimes.toLocalDate(bloodPressure.getMeasuredAt()), bloodPressure.getId());
            }

            for (HabitImportRow row : userHabits) {
                Habit habit = new Habit();
                habit.setLegacyFirebaseId(row.id());
                habit.setUser(user);
                habit.setStartDate(toOffsetDateTime(row.startDate()));
                habit.setDuration(row.duration());
                habit.setLastTimeDate(row.lastTimeDate() == null ? null : toOffsetDateTime(row.lastTimeDate()));
                habit.setName(row.name());
                habit.setTimes(row.times());
                habit.setCurrentStrike(row.currentStrike());
                habit.setBestStrike(row.bestStrike());
                habitRepository.save(habit);
            }

            List<ImportedRoutine> importedRoutines = new ArrayList<>();
            for (RoutineImportRow row : userRoutines) {
                Routine routine = new Routine();
                routine.setLegacyFirebaseId(row.id());
                routine.setUser(user);
                routine.setStartDate(toOffsetDateTime(row.startDate()));
                routine.setLastTimeDate(row.lastTimeDate() == null ? null : toOffsetDateTime(row.lastTimeDate()));
                routine.setName(row.name());
                routine.setCurrentStrike(row.currentStrike());
                routine.setBestStrike(row.bestStrike());
                routine.setTypes(row.types().stream().map(TypeRow::name).map(RoutineType::valueOf).collect(Collectors.toCollection(LinkedHashSet::new)));
                routineRepository.save(routine);

                List<LocalDate> checkinDates = new ArrayList<>();
                for (String time : new LinkedHashSet<>(row.times())) {
                    OffsetDateTime checkedAt = toOffsetDateTime(time);
                    RoutineCheckin checkin = new RoutineCheckin();
                    checkin.setRoutine(routine);
                    checkin.setCheckedAt(checkedAt);
                    routineCheckinRepository.save(checkin);
                    checkinDates.add(DateTimes.toLocalDate(checkedAt));
                }
                checkinDates.sort(LocalDate::compareTo);
                importedRoutines.add(new ImportedRoutine(routine.getTypes(), DateTimes.toLocalDate(routine.getStartDate()), new HashSet<>(checkinDates), checkinDates));
            }

            for (Map.Entry<String, PhotoMappingRow> entry : photoMapping.entrySet()) {
                Long weightId = importedWeightIds.get(entry.getKey());
                if (weightId == null) {
                    continue;
                }
                Weight weight = weightRepository.getReferenceById(weightId);
                PhotoMappingRow row = entry.getValue();
                if (row.frontLocalPath() != null) {
                    weight.setPhotoFrontPath(photoStorageService.importWeightPhoto(weight, "front", properties.importData().photoRootPath().resolve(row.frontLocalPath()).normalize()));
                }
                if (row.leftLocalPath() != null) {
                    weight.setPhotoLeftPath(photoStorageService.importWeightPhoto(weight, "left", properties.importData().photoRootPath().resolve(row.leftLocalPath()).normalize()));
                }
                if (row.rightLocalPath() != null) {
                    weight.setPhotoRightPath(photoStorageService.importWeightPhoto(weight, "right", properties.importData().photoRootPath().resolve(row.rightLocalPath()).normalize()));
                }
            }

            LocalDate anchorDate = weightIdsByDate.isEmpty() ? LocalDate.now(DateTimes.USER_ZONE) : weightIdsByDate.lastKey();
            LocalDate firstStatusDate = userRoutines.stream()
                .map(RoutineImportRow::startDate)
                .map(this::toOffsetDateTime)
                .map(DateTimes::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(anchorDate);
            user.setDashboardAnchorDate(anchorDate);
            userRepository.save(user);

            return new ImportContext(user.getId(), firstStatusDate, anchorDate, weightIdsByDate, bloodPressureIdsByDate, importedRoutines);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void backfillDailyStatuses(ImportContext context) {
        LocalDate current = context.firstStatusDate();
        while (!current.isAfter(context.anchorDate())) {
            LocalDate batchStart = current;
            LocalDate batchEnd = current.plusDays(DAILY_STATUS_BATCH_DAYS - 1);
            if (batchEnd.isAfter(context.anchorDate())) {
                batchEnd = context.anchorDate();
            }
            LocalDate finalBatchStart = batchStart;
            LocalDate finalBatchEnd = batchEnd;
            transactionTemplate.executeWithoutResult(status -> saveDailyStatusBatch(context, finalBatchStart, finalBatchEnd));
            log.info("Backfilled daily statuses userId={} from={} to={}", context.userId(), batchStart, batchEnd);
            current = batchEnd.plusDays(1);
        }
    }

    private void saveDailyStatusBatch(ImportContext context, LocalDate start, LocalDate end) {
        List<DailyStatus> batch = new ArrayList<>();
        User user = userRepository.getReferenceById(context.userId());
        LocalDate current = start;
        while (!current.isAfter(end)) {
            batch.add(buildDailyStatus(user, context, current));
            current = current.plusDays(1);
        }
        dailyStatusRepository.saveAll(batch);
    }

    private DailyStatus buildDailyStatus(User user, ImportContext context, LocalDate date) {
        List<ImportedRoutine> activeRoutines = context.routines().stream()
            .filter(routine -> !routine.startDate().isAfter(date))
            .toList();
        List<ImportedRoutine> doneRoutines = activeRoutines.stream()
            .filter(routine -> routine.doneDates().contains(date))
            .toList();

        DailyStatus dailyStatus = new DailyStatus();
        dailyStatus.setUser(user);
        dailyStatus.setStatusDate(date);

        Map.Entry<LocalDate, Long> weightEntry = context.weightIdsByDate().floorEntry(date);
        if (weightEntry != null) {
            dailyStatus.setWeight(weightRepository.getReferenceById(weightEntry.getValue()));
        }

        Map.Entry<LocalDate, Long> bloodPressureEntry = context.bloodPressureIdsByDate().floorEntry(date);
        if (bloodPressureEntry != null) {
            dailyStatus.setBloodPressure(bloodPressureRepository.getReferenceById(bloodPressureEntry.getValue()));
        }

        int totalRoutines = activeRoutines.size();
        int totalWeightRoutines = countByType(activeRoutines, RoutineType.WEIGHT);
        int totalBloodPressureRoutines = countByType(activeRoutines, RoutineType.BLOOD_PRESSURE);
        int totalFlexibilityRoutines = countByType(activeRoutines, RoutineType.FLEXIBILITY);
        int totalMindRoutines = countByType(activeRoutines, RoutineType.MIND);
        int routinesDone = doneRoutines.size();
        int weightDone = countByType(doneRoutines, RoutineType.WEIGHT);
        int bloodPressureDone = countByType(doneRoutines, RoutineType.BLOOD_PRESSURE);
        int flexibilityDone = countByType(doneRoutines, RoutineType.FLEXIBILITY);
        int mindDone = countByType(doneRoutines, RoutineType.MIND);

        dailyStatus.setTotalRoutines(totalRoutines);
        dailyStatus.setTotalWeightRoutines(totalWeightRoutines);
        dailyStatus.setTotalBloodPressureRoutines(totalBloodPressureRoutines);
        dailyStatus.setTotalFlexibilityRoutines(totalFlexibilityRoutines);
        dailyStatus.setTotalMindRoutines(totalMindRoutines);
        dailyStatus.setRoutinesDone(routinesDone);
        dailyStatus.setWeightDone(weightDone);
        dailyStatus.setBloodPressureDone(bloodPressureDone);
        dailyStatus.setFlexibilityDone(flexibilityDone);
        dailyStatus.setMindDone(mindDone);
        dailyStatus.setRoutinesPercentage(Numbers.percentage(routinesDone, totalRoutines));
        dailyStatus.setWeightPercentage(Numbers.percentage(weightDone, totalWeightRoutines));
        dailyStatus.setBloodPressurePercentage(Numbers.percentage(bloodPressureDone, totalBloodPressureRoutines));
        dailyStatus.setFlexibilityPercentage(Numbers.percentage(flexibilityDone, totalFlexibilityRoutines));
        dailyStatus.setMindPercentage(Numbers.percentage(mindDone, totalMindRoutines));

        BigDecimal routinesScore = score(activeRoutines, date);
        BigDecimal weightScore = score(filterByType(activeRoutines, RoutineType.WEIGHT), date);
        BigDecimal bloodPressureScore = score(filterByType(activeRoutines, RoutineType.BLOOD_PRESSURE), date);
        BigDecimal flexibilityScore = score(filterByType(activeRoutines, RoutineType.FLEXIBILITY), date);
        BigDecimal mindScore = score(filterByType(activeRoutines, RoutineType.MIND), date);
        dailyStatus.setRoutinesScore(routinesScore);
        dailyStatus.setWeightScore(weightScore);
        dailyStatus.setBloodPressureScore(bloodPressureScore);
        dailyStatus.setFlexibilityScore(flexibilityScore);
        dailyStatus.setMindScore(mindScore);
        dailyStatus.setRoutinesStatus(percentage(routinesScore, totalRoutines));
        dailyStatus.setWeightStatus(percentage(weightScore, totalWeightRoutines));
        dailyStatus.setBloodPressureStatus(percentage(bloodPressureScore, totalBloodPressureRoutines));
        dailyStatus.setFlexibilityStatus(percentage(flexibilityScore, totalFlexibilityRoutines));
        dailyStatus.setMindStatus(percentage(mindScore, totalMindRoutines));

        return dailyStatus;
    }

    private int countByType(Collection<ImportedRoutine> routines, RoutineType type) {
        return (int) routines.stream().filter(routine -> routine.types().contains(type)).count();
    }

    private List<ImportedRoutine> filterByType(List<ImportedRoutine> routines, RoutineType type) {
        return routines.stream().filter(routine -> routine.types().contains(type)).toList();
    }

    private BigDecimal score(List<ImportedRoutine> routines, LocalDate date) {
        return Numbers.round(routines.stream()
            .map(routine -> routineScore(routine, date))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal routineScore(ImportedRoutine routine, LocalDate date) {
        BigDecimal status = routineStatus(routine, date);
        if (status.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return BigDecimal.ONE;
        }
        if (status.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return BigDecimal.valueOf(0.75);
        }
        if (status.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return BigDecimal.valueOf(0.5);
        }
        if (status.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return BigDecimal.valueOf(0.25);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal routineStatus(ImportedRoutine routine, LocalDate date) {
        long days = Math.min(31, ChronoUnit.DAYS.between(routine.startDate(), date.plusDays(1)));
        if (days <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long count = routine.countBetween(date.minusDays(31), date);
        BigDecimal percentage = BigDecimal.valueOf(count)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return percentage.min(BigDecimal.valueOf(100));
    }

    private BigDecimal percentage(BigDecimal number, int total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return number.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void applyWeightDeltas(Weight current, Weight previous) {
        if (previous == null) {
            current.setLostWeight(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            current.setLostFat(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            current.setLostMuscle(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return;
        }
        current.setLostWeight(Numbers.round(current.getWeight().subtract(previous.getWeight())));
        current.setLostFat(Numbers.round(current.getFat().subtract(previous.getFat())));
        current.setLostMuscle(Numbers.round(current.getMuscle().subtract(previous.getMuscle())));
    }

    private void applyBloodPressureDeltas(BloodPressure current, BloodPressure previous) {
        if (previous == null) {
            current.setLostUpper(0);
            current.setLostLower(0);
            return;
        }
        current.setLostUpper(current.getUpper() - previous.getUpper());
        current.setLostLower(current.getLower() - previous.getLower());
    }

    private Map<String, PhotoMappingRow> loadPhotoMapping() throws IOException {
        if (!Files.exists(properties.importData().photoMappingPath())) {
            return Map.of();
        }
        List<PhotoMappingRow> rows = objectMapper.readValue(properties.importData().photoMappingPath().toFile(), new TypeReference<>() {});
        return rows.stream()
            .filter(row -> row.firebaseWeightId() != null)
            .collect(Collectors.toMap(PhotoMappingRow::firebaseWeightId, row -> row));
    }

    private Map<String, String> extractSections(String html) {
        List<String> labels = List.of("WEIGHTS", "BLOOD PRESSURES", "HABITS", "ROUTINES");
        Map<String, String> sections = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int start = html.indexOf("<h1>" + label + "</h1>");
            int next = i + 1 < labels.size() ? html.indexOf("<h1>" + labels.get(i + 1) + "</h1>") : html.indexOf("</div><div class=\"p-toast", start);
            sections.put(label, html.substring(start + ("<h1>" + label + "</h1>").length(), next).trim());
        }
        return sections;
    }

    private OffsetDateTime toOffsetDateTime(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WeightImportRow(
        String id,
        String user,
        String date,
        BigDecimal weight,
        BigDecimal fat_percentage,
        BigDecimal fat,
        BigDecimal muscle,
        BigDecimal muscle_percentage,
        BigDecimal lost_weight,
        BigDecimal lost_fat,
        BigDecimal lost_muscle
    ) {
        BigDecimal fatPercentage() { return fat_percentage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BloodPressureImportRow(String id, String user, String date, Integer upper, Integer lower) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HabitImportRow(
        String id,
        String user,
        String start_date,
        Integer duration,
        String last_time_date,
        String name,
        Integer times,
        Integer current_strike,
        Integer best_strike
    ) {
        String startDate() { return start_date; }
        String lastTimeDate() { return last_time_date; }
        Integer currentStrike() { return current_strike; }
        Integer bestStrike() { return best_strike; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoutineImportRow(
        String id,
        String user,
        String start_date,
        String last_time_date,
        String name,
        List<String> times,
        Integer current_strike,
        Integer best_strike,
        List<TypeRow> types
    ) {
        String startDate() { return start_date; }
        String lastTimeDate() { return last_time_date; }
        Integer currentStrike() { return current_strike; }
        Integer bestStrike() { return best_strike; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TypeRow(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotoMappingRow(String firebaseWeightId, String frontLocalPath, String leftLocalPath, String rightLocalPath) {
    }

    private record ImportContext(
        Long userId,
        LocalDate firstStatusDate,
        LocalDate anchorDate,
        NavigableMap<LocalDate, Long> weightIdsByDate,
        NavigableMap<LocalDate, Long> bloodPressureIdsByDate,
        List<ImportedRoutine> routines
    ) {
    }

    private record ImportedRoutine(Set<RoutineType> types, LocalDate startDate, Set<LocalDate> doneDates, List<LocalDate> checkinDates) {

        long countBetween(LocalDate startInclusive, LocalDate endInclusive) {
            int startIndex = lowerBound(checkinDates, startInclusive);
            int endIndex = upperBound(checkinDates, endInclusive);
            return Math.max(0, endIndex - startIndex);
        }

        private static int lowerBound(List<LocalDate> values, LocalDate target) {
            int low = 0;
            int high = values.size();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (values.get(mid).isBefore(target)) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low;
        }

        private static int upperBound(List<LocalDate> values, LocalDate target) {
            int low = 0;
            int high = values.size();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (values.get(mid).isAfter(target)) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return low;
        }
    }
}
