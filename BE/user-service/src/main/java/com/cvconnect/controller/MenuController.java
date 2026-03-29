package com.cvconnect.controller;

import com.cvconnect.dto.menu.MenuMetadata;
import com.cvconnect.enums.MemberType;
import com.cvconnect.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
public class MenuController {
    @Autowired
    private MenuService menuService;

    @GetMapping("/menu-by-role/{roleId}")
    @Operation(summary = "Get menus by role ID")
    public ResponseEntity<Response<List<MenuMetadata>>> getMenusByRoleId(@PathVariable("roleId") Long roleId) {
        return ResponseUtils.success(menuService.getMenusByRoleId(roleId));
    }

    @GetMapping("/all-menus")
    @Operation(summary = "Get all menus")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:VIEW')")
    public ResponseEntity<Response<List<MenuMetadata>>> getAllMenus(@RequestParam(name = "memberType", required = true) MemberType memberType) {
        return ResponseUtils.success(menuService.getAllMenus(memberType));
    }
}
