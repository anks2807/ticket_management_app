package com.ttn.tickets.assistant.refresh;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttn.tickets.assistant.web.dto.RefreshStatusResponse;
import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.persistence.RefreshIntentRepository;

@Service
public class RefreshStatusService {

    private final RefreshIntentRepository refreshIntentRepository;

    public RefreshStatusService(RefreshIntentRepository refreshIntentRepository) {
        this.refreshIntentRepository = refreshIntentRepository;
    }

    @Transactional(readOnly = true)
    public RefreshStatusResponse status() {
        long pending = refreshIntentRepository.countByProcessingStatus(ProcessingStatus.PENDING);
        long failed = refreshIntentRepository.countByProcessingStatus(ProcessingStatus.FAILED);
        return new RefreshStatusResponse(pending, failed);
    }
}
