package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.config.NormalStartup;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@NormalStartup
public class PersonalRecordStartupRebuild implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PersonalRecordService personalRecordService;

    public PersonalRecordStartupRebuild(UserRepository userRepository, PersonalRecordService personalRecordService) {
        this.userRepository = userRepository;
        this.personalRecordService = personalRecordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findAll().forEach(personalRecordService::rebuild);
    }
}
