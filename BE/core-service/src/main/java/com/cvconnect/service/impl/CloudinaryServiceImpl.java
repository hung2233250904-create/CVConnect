package com.cvconnect.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cvconnect.dto.attachFile.AttachFileDto;
import com.cvconnect.enums.CoreErrorCode;
import com.cvconnect.service.CloudinaryService;
import nmquan.commonlib.exception.AppException;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {
    @Autowired
    private Cloudinary cloudinary;

    private static final int MAX_FILE_QUANTITY = 5;
    private static final String FOLDER_BASE = "cv-connect/";
    private static final String SUPPORTED_FILE_TYPES = "jpg, png, pdf, doc, docx";
    private static final int MAX_FILE_SIZE_MB = 5;

    @Override
    public List<AttachFileDto> uploadFile(MultipartFile[] files, String folder) {
        try {
            if(files.length > MAX_FILE_QUANTITY){
                throw new AppException(CoreErrorCode.UPLOAD_FILE_QUANTITY_EXCEED_LIMIT, MAX_FILE_QUANTITY);
            }
            for(MultipartFile file : files){
                if(!isAllowedFile(file.getContentType())){
                    throw new AppException(CoreErrorCode.FILE_FORMAT_NOT_SUPPORTED, SUPPORTED_FILE_TYPES);
                }
                if(file.getSize() > MAX_FILE_SIZE_MB * 1024 * 1024) {
                    throw new AppException(CoreErrorCode.FILE_TOO_LARGE, MAX_FILE_SIZE_MB);
                }
            }
            if(folder == null || folder.isEmpty()){
                folder = "";
            }

            List<AttachFileDto> attachFileDtos = new ArrayList<>();
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                String baseName = FilenameUtils.getBaseName(originalFilename);
                String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();

                String newFileName = baseName + "_" + System.currentTimeMillis();
                if (extension.matches("doc|docx")) {
                    newFileName = newFileName + "." + extension;
                }
                Map map = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                        "folder", FOLDER_BASE + folder,
                        "resource_type", "auto",
                        "public_id", newFileName
                ));
                AttachFileDto attachFileDto = AttachFileDto.builder()
                        .originalFilename(originalFilename)
                        .baseFilename(baseName)
                        .extension(extension)
                        .filename(newFileName)
                        .format(map.get("format")!= null ? map.get("format").toString() : null)
                        .resourceType(map.get("resource_type").toString())
                        .secureUrl(map.get("secure_url").toString())
                        .type(map.get("type").toString())
                        .url(map.get("url").toString())
                        .publicId(map.get("public_id").toString())
                        .folder(map.get("folder").toString())
                        .build();
                attachFileDtos.add(attachFileDto);
            }
            return attachFileDtos;
        } catch (IOException e) {
            throw new AppException(CoreErrorCode.UPLOAD_FILE_ERROR);
        }
    }

    @Override
    public void deleteByPublicIds(List<String> publicIds) {
        publicIds.forEach(publicId -> {
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            } catch (IOException ignored) {

            }
        });
    }

    @Override
    public String generateSignedUrl(AttachFileDto attachFileDto) {
        if (attachFileDto == null) {
            return null;
        }
        if (attachFileDto.getPublicId() == null || attachFileDto.getPublicId().isEmpty()) {
            return attachFileDto.getSecureUrl();
        }

        try {
            String resourceType = attachFileDto.getResourceType();
            if (resourceType == null || resourceType.isEmpty()) {
                resourceType = "auto";
            }

            return cloudinary.url()
                    .secure(true)
                    .signed(true)
                    .resourceType(resourceType)
                    .generate(attachFileDto.getPublicId());
        } catch (Exception ignored) {
            return attachFileDto.getSecureUrl();
        }
    }

    private boolean isAllowedFile(String contentType) {
        return contentType != null && (
                contentType.equals("image/jpeg") ||     // .jpg, .jpeg
                        contentType.equals("image/png") ||      // .png
                        contentType.equals("application/pdf") ||    // .pdf
                        contentType.equals("application/msword") ||   // .doc
                        contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")   // .docx
        );
    }

}
