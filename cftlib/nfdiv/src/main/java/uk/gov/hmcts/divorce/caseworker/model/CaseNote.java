package uk.gov.hmcts.divorce.caseworker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.ccd.sdk.api.CCD;

import java.time.LocalDateTime;

import static uk.gov.hmcts.ccd.sdk.type.FieldType.TextArea;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseNote {

    @CCD(
        label = "Author"
    )
    private String author;

    @CCD(
        label = "Date"
    )
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;


    @CCD(
        label = "Note",
        typeOverride = TextArea
    )
    private String note;

}
