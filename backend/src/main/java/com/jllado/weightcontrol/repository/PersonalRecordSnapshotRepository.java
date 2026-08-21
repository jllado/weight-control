package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.PersonalRecordSnapshot;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecordSnapshotRepository extends JpaRepository<PersonalRecordSnapshot, Long> {

    @EntityGraph(attributePaths = "exercise")
    List<PersonalRecordSnapshot> findByUser(User user);

    void deleteByUser(User user);
}
