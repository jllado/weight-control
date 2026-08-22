package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findByUserOrderByNameAsc(User user);
}
