package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachNoteDtos.CoachNoteRequest;
import com.jllado.weightcontrol.domain.CoachNote;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CoachNoteRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CoachNoteService {
    private final CoachNoteRepository repository;
    public CoachNoteService(CoachNoteRepository repository) { this.repository = repository; }
    public List<CoachNote> findAll(User user) { return repository.findByUserOrderByNoteDateDescIdDesc(user); }
    public List<CoachNote> findBetween(User user, LocalDate from, LocalDate to) { return repository.findByUserAndNoteDateBetweenOrderByNoteDateAscIdAsc(user, from, to); }
    public long count(User user) { return repository.countByUser(user); }
    public CoachNote create(User user, CoachNoteRequest request) {
        validateDate(request.date());
        CoachNote note = new CoachNote(); note.setUser(user); apply(note, request); return repository.save(note);
    }
    public CoachNote update(User user, Long id, CoachNoteRequest request) { validateDate(request.date()); CoachNote note = owned(user, id); apply(note, request); return repository.save(note); }
    public void delete(User user, Long id) { repository.delete(owned(user, id)); }
    private CoachNote owned(User user, Long id) { return repository.findByUserAndId(user, id).orElseThrow(() -> new NotFoundException("Coach note not found")); }
    private void validateDate(LocalDate date) { if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) throw new BadRequestException("Coach note date cannot be in the future"); }
    private void apply(CoachNote note, CoachNoteRequest request) { note.setNoteDate(request.date()); note.setContent(request.content().strip()); }
}
