package com.kologeski.interviewassistant.deployment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InstallationStateRepository extends JpaRepository<InstallationState, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from InstallationState state where state.id = 1")
    java.util.Optional<InstallationState> lockSingleton();
}
