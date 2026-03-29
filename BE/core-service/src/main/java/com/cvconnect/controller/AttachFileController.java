package com.cvconnect.controller;

import com.cvconnect.constant.Messages;
import com.cvconnect.dto.attachFile.AttachFileDto;
import com.cvconnect.service.AttachFileService;
import io.swagger.v3.oas.annotations.Operation;
import nmquan.commonlib.annotation.InternalRequest;
import nmquan.commonlib.constant.MessageConstants;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.LocalizationUtils;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/attach-file")
public class AttachFileController {
    @Autowired
    private AttachFileService attachFileService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @PostMapping("/upload")
    @Operation(summary = "Upload Attach File API")
    public ResponseEntity<Response<IDResponse<Long>>> uploadFile(@RequestParam MultipartFile file) {
        MultipartFile[] multipartFile = new MultipartFile[]{file};
        List<Long> ids = attachFileService.uploadFile(multipartFile);
        return ResponseUtils.success(
                IDResponse.<Long>builder()
                        .id(ids.get(0))
                        .build(),
                localizationUtils.getLocalizedMessage(Messages.UPLOAD_FILE_SUCCESS, 1));
    }

    @InternalRequest
    @PostMapping("/internal/upload")
    @Operation(summary = "Upload Attach File")
    public ResponseEntity<Response<IDResponse<Long>>> uploadFileInternal(@RequestParam MultipartFile file) {
        MultipartFile[] multipartFile = new MultipartFile[]{file};
        List<Long> ids = attachFileService.uploadFile(multipartFile);
        return ResponseUtils.success(
                IDResponse.<Long>builder()
                        .id(ids.get(0))
                        .build(),
                localizationUtils.getLocalizedMessage(Messages.UPLOAD_FILE_SUCCESS, 1));
    }

    @InternalRequest
    @PostMapping("/internal/uploads")
    @Operation(summary = "Upload Attach File")
    public ResponseEntity<Response<List<Long>>> uploadFilesInternal(@RequestParam MultipartFile[] files) {
        return ResponseUtils.success(attachFileService.uploadFile(files), localizationUtils.getLocalizedMessage(Messages.UPLOAD_FILE_SUCCESS, 1));
    }

    @InternalRequest
    @GetMapping("/internal/get-by-id/{id}")
    @Operation(summary = "Get Attach File by ID")
    public ResponseEntity<Response<AttachFileDto>> getAttachFile(@PathVariable Long id) {
        return ResponseUtils.success(attachFileService.getAttachFiles(List.of(id)).get(0));
    }

    @InternalRequest
    @PostMapping("/internal/delete-by-ids")
    @Operation(summary = "Delete Attach File by ID")
    public ResponseEntity<Response<Void>> deleteAttachFile(@RequestBody List<Long> ids) {
        attachFileService.deleteByIds(ids);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.DELETE_SUCCESSFULLY));
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "Download Attach File")
    public ResponseEntity<InputStreamResource> downloadAttachFile(@PathVariable("id") Long id) {
        var file = attachFileService.download(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.getContentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + file.getFilename())
                .body(new InputStreamResource(file.getByteArrayInputStream()));
    }
}
