package uk.gov.hmcts.ccd.domain.service.createevent;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.CaseDataContent;
import uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationServiceImpl;

@Service
@Qualifier("classified")
public class ClassifiedCreateEventOperation implements CreateEventOperation {
    private final CreateEventOperation createEventOperation;
    private final SecurityClassificationServiceImpl classificationService;

    @Autowired
    public ClassifiedCreateEventOperation(@Qualifier("default") CreateEventOperation createEventOperation,
                                          SecurityClassificationServiceImpl classificationService) {

        this.createEventOperation = createEventOperation;
        this.classificationService = classificationService;
    }

    @Override
    public CaseDetails createCaseEvent(String caseReference,
                                       CaseDataContent content) {
        return createEventOperation.createCaseEvent(caseReference,
                                                                           content);
    }

    @Override
    public CaseDetails createCaseSystemEvent(String caseReference, Integer version,
                                             String attributePath, String categoryId) {
        return createEventOperation.createCaseSystemEvent(caseReference,
            version, attributePath, categoryId);
    }
}
