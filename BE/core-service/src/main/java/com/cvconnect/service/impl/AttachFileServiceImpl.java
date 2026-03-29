package com.cvconnect.service.impl;

import com.cvconnect.constant.Constants;
import com.cvconnect.dto.attachFile.AttachFileDto;
import com.cvconnect.dto.attachFile.DownloadFileDto;
import com.cvconnect.entity.AttachFile;
import com.cvconnect.enums.CoreErrorCode;
import com.cvconnect.repository.AttachFileRepository;
import com.cvconnect.service.AttachFileService;
import com.cvconnect.service.CloudinaryService;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.model.BaseEntity;
import nmquan.commonlib.utils.ObjectMapperUtils;
import nmquan.commonlib.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttachFileServiceImpl implements AttachFileService {
    @Autowired
    private AttachFileRepository attachFileRepository;
    @Autowired
    private CloudinaryService cloudinaryService;

    @Qualifier(CommonConstants.EXTERNAL)
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public List<Long> uploadFile(MultipartFile[] files) {
        String username = WebUtils.getCurrentUsername();
        if(username == null) {
            username = Constants.RoleCode.ANONYMOUS;
        }
        List<AttachFileDto> attachFileDtos = cloudinaryService.uploadFile(files, username);
        List<AttachFile> attachFiles = attachFileDtos.stream()
                .map(dto -> ObjectMapperUtils.convertToObject(dto, AttachFile.class))
                .toList();
        attachFileRepository.saveAll(attachFiles);
        return attachFiles.stream()
                .map(BaseEntity::getId)
                .toList();
    }

    @Override
    public List<AttachFileDto> getAttachFiles(List<Long> ids) {
        List<AttachFile> attachFiles = attachFileRepository.findAllById(ids);
        if(ids.size() != attachFiles.size()){
            throw new AppException(CoreErrorCode.ATTACH_FILE_NOT_FOUND);
        }
        return attachFiles.stream()
                .map(attachFile -> ObjectMapperUtils.convertToObject(attachFile, AttachFileDto.class))
                .toList();
    }

    @Override
    public DownloadFileDto download(Long id) {
        AttachFile attachFile = attachFileRepository.findById(id)
                .orElseThrow(() -> new AppException(CoreErrorCode.ATTACH_FILE_NOT_FOUND));

        AttachFileDto attachFileDto = ObjectMapperUtils.convertToObject(attachFile, AttachFileDto.class);

        List<String> candidateUrls = new ArrayList<>();
        String privateDownloadUrl = cloudinaryService.generatePrivateDownloadUrl(attachFileDto);
        if (privateDownloadUrl != null && !privateDownloadUrl.isBlank()) {
            candidateUrls.add(privateDownloadUrl);
        }
        String signedUrl = cloudinaryService.generateSignedUrl(attachFileDto);
        if (signedUrl != null && !signedUrl.isBlank()) {
            candidateUrls.add(signedUrl);
        }
        if (attachFile.getSecureUrl() != null && !attachFile.getSecureUrl().isBlank()) {
            candidateUrls.add(attachFile.getSecureUrl());
        }
        if (attachFile.getUrl() != null && !attachFile.getUrl().isBlank()) {
            candidateUrls.add(attachFile.getUrl());
        }

        byte[] fileBytes = null;
        for (String fileUrl : candidateUrls) {
            try {
                URI uri = URI.create(fileUrl);
                ResponseEntity<byte[]> responseEntity = restTemplate.exchange(uri, HttpMethod.GET, null, byte[].class);
                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null && responseEntity.getBody().length > 0) {
                    fileBytes = responseEntity.getBody();
                    break;
                }
            } catch (Exception ignored) {
                // Try next URL candidate.
            }
        }

        if (fileBytes == null) {
            throw new AppException(CoreErrorCode.DOWNLOAD_FILE_FAILED);
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
        String encodedFileName = URLEncoder.encode(attachFile.getOriginalFilename(), StandardCharsets.UTF_8);
        return DownloadFileDto.builder()
                .attachFileId(attachFile.getId())
                .filename(encodedFileName)
                .contentType(resolveContentType(attachFile.getExtension()))
                .byteArrayInputStream(byteArrayInputStream)
                .build();
    }

    private String resolveContentType(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }

        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        List<AttachFile> attachFiles = attachFileRepository.findAllById(ids);
        if(ids.size() != attachFiles.size()){
            throw new AppException(CoreErrorCode.ATTACH_FILE_NOT_FOUND);
        }
        attachFileRepository.deleteAll(attachFiles);
        cloudinaryService.deleteByPublicIds(
                attachFiles.stream()
                        .map(AttachFile::getPublicId)
                        .toList()
        );
    }
}
