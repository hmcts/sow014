package uk.gov.hmcts.ccd.domain.service.getcase;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;

import java.util.Optional;
import uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationServiceImpl;

@Service
@Qualifier("classified")
public class ClassifiedGetCaseOperation implements GetCaseOperation {


    private final GetCaseOperation getCaseOperation;
    private final SecurityClassificationServiceImpl classificationService;

    public ClassifiedGetCaseOperation(@Qualifier("default") GetCaseOperation getCaseOperation,
                                      SecurityClassificationServiceImpl classificationService) {
        this.getCaseOperation = getCaseOperation;
        this.classificationService = classificationService;
    }

    @Override
    public Optional<CaseDetails> execute(String jurisdictionId, String caseTypeId, String caseReference) {
        // SOW014: Disable this unused feature
        return getCaseOperation.execute(jurisdictionId, caseTypeId, caseReference);
    }

    @Override
    public Optional<CaseDetails> execute(String caseReference) {
        // SOW014: Disable this unused feature
        return getCaseOperation.execute(caseReference);
    }
}
