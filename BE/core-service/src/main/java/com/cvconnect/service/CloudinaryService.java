package com.cvconnect.service;

import com.cvconnect.dto.attachFile.AttachFileDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CloudinaryService {
    List<AttachFileDto> uploadFile(MultipartFile[] files, String folder);
    void deleteByPublicIds(List<String> publicIds);
    String generateSignedUrl(AttachFileDto attachFileDto);
}
