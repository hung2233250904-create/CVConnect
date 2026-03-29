package com.cvconnect.dto.attachFile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.InputStreamResource;

import java.io.ByteArrayInputStream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownloadFileDto {
    private Long attachFileId;
    private String filename;
    private String contentType;
    private ByteArrayInputStream byteArrayInputStream;
    private InputStreamResource resource;
}
