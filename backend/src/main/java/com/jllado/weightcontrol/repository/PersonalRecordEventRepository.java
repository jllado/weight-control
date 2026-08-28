package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonalRecordEventRepository extends JpaRepository<PersonalRecordEvent, Long>, JpaSpecificationExecutor<PersonalRecordEvent> {
    @EntityGraph(attributePaths = "exercise")
    List<PersonalRecordEvent> findByUserAndSourceTypeAndSourceIdIn(User user, PersonalRecordSourceType sourceType, Collection<Long> sourceIds);

    @EntityGraph(attributePaths = "exercise")
    List<PersonalRecordEvent> findByUser(User user);
    void deleteByUser(User user);
}
