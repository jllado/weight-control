package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.PersonalRecordSetting;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecordSettingRepository extends JpaRepository<PersonalRecordSetting, Long> {
    List<PersonalRecordSetting> findByUser(User user);
    void deleteByUser(User user);
}
