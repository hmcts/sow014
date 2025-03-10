package uk.gov.hmcts.ccd.data.casedetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.ApplicationParams;
import uk.gov.hmcts.ccd.clients.PocApiClient;
import uk.gov.hmcts.ccd.data.SecurityUtils;
import uk.gov.hmcts.ccd.data.casedetails.search.MetaData;
import uk.gov.hmcts.ccd.data.casedetails.search.PaginatedSearchMetadata;
import uk.gov.hmcts.ccd.domain.model.casedataaccesscontrol.RoleAssignments;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.migration.MigrationParameters;
import uk.gov.hmcts.ccd.domain.model.std.CaseAssignedUserRole;
import uk.gov.hmcts.ccd.domain.service.casedataaccesscontrol.RoleAssignmentService;
import uk.gov.hmcts.ccd.domain.service.common.DefaultObjectMapperService;
import uk.gov.hmcts.ccd.util.ClientContextUtil;

@Service
@Slf4j
public class POCCaseDetailsRepository implements CaseDetailsRepository {

    private final ApplicationParams applicationParams;
    private final PocApiClient pocApiClient;
    private final SecurityUtils securityUtils;
    private final RoleAssignmentService roleAssignmentService;
    private final DefaultObjectMapperService objectMapperService;

    public POCCaseDetailsRepository(final ApplicationParams applicationParams,
                                    final PocApiClient pocApiClient,
                                    final SecurityUtils securityUtils,
                                    final RoleAssignmentService roleAssignmentService,
                                    final DefaultObjectMapperService objectMapperService) {
        this.applicationParams = applicationParams;
        this.pocApiClient = pocApiClient;
        this.securityUtils = securityUtils;
        this.roleAssignmentService = roleAssignmentService;
        this.objectMapperService = objectMapperService;
    }

    @Override
    public CaseDetails set(CaseDetails caseDetails) {
        return null;
    }

    @Override
    public Optional<CaseDetails> findById(String jurisdiction, Long id) {
        return Optional.empty();
    }

    @Override
    public CaseDetails findById(Long id) {
        return null;
    }

    @Override
    public List<Long> findCaseReferencesByIds(List<Long> ids) {
        return List.of();
    }

    @Override
    public Optional<CaseDetails> findByReferenceWithNoAccessControl(String reference) {
        return Optional.ofNullable(getCaseDetails(reference));
    }

    @Override
    public Optional<CaseDetails> findByReference(String jurisdiction, Long caseReference) {
        return Optional.ofNullable(getCaseDetails(caseReference.toString()));
    }

    @Override
    public Optional<CaseDetails> findByReference(String jurisdiction, String reference) {
        return Optional.ofNullable(getCaseDetails(reference));
    }

    @Override
    public Optional<CaseDetails> findByReference(String reference) {
        return Optional.ofNullable(getCaseDetails(reference));
    }

    @Override
    public CaseDetails findByReference(Long caseReference) {
        return getCaseDetails(caseReference.toString());
    }

    private CaseDetails getCaseDetails(String reference) {
        List<CaseAssignedUserRole> roleAssignments = roleAssignmentService
            .findRoleAssignmentsByCasesAndUsers(List.of(reference), List.of(securityUtils.getUserId()));
        CaseDetails caseDetails = pocApiClient.getCase(reference,
            ClientContextUtil.encodeToBase64(objectMapperService.convertObjectToString(roleAssignments)));
        log.info("case Id {}", caseDetails.getId());
        log.info("case reference {}", caseDetails.getReference());
        if (Optional.ofNullable(caseDetails).isPresent()) {
            caseDetails.setId(caseDetails.getReference().toString());
            caseDetails.setReference(Long.valueOf(reference));
            log.info("case Id after{}", caseDetails.getId());
            log.info("case reference after{}", caseDetails.getReference());
        }
        return caseDetails;
    }


    @Override
    public CaseDetails findUniqueCase(String jurisdictionId, String caseTypeId, String caseReference) {
        return getCaseDetails(caseReference);
    }

    @Override
    public List<CaseDetails> findByMetaDataAndFieldData(MetaData metadata, Map<String, String> dataSearchParams) {
        return List.of();
    }

    @Override
    public List<CaseDetails> findByParamsWithLimit(MigrationParameters migrationParameters) {
        return List.of();
    }

    @Override
    public PaginatedSearchMetadata getPaginatedSearchMetadata(MetaData metaData, Map<String, String> dataSearchParams) {
        return null;
    }
}
