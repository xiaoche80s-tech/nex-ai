package com.gkht.ai.nexai.module.ai.skill.interfaces.controller.admin.skill;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionContentDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;
import com.gkht.ai.nexai.module.ai.skill.application.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * Skill 资产管理控制器（包名含 controller.admin，挂 /admin-api）。
 */
@Tag(name = "管理后台 - Skill 资产")
@RestController
@RequestMapping("/ai/skill")
@Validated
public class SkillController {

    @Resource
    private SkillService skillService;

    @PostMapping("/create")
    @Operation(summary = "创建 Skill", description = "创建 skill 并登记首个版本，物化到文件目录供 agentscope 读取")
    @PreAuthorize("@ss.hasPermission('ai:skill:create')")
    public CommonResult<Long> createSkill(@Valid @RequestBody SkillCreateCommand command) {
        return success(skillService.createSkill(command, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/version")
    @Operation(summary = "登记 Skill 版本", description = "编辑产生新版本（版本链只增不改），物化到文件目录")
    @PreAuthorize("@ss.hasPermission('ai:skill:update')")
    public CommonResult<Integer> addSkillVersion(@Valid @RequestBody SkillVersionCommand command) {
        return success(skillService.addSkillVersion(command, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 Skill 分页", description = "归属可见性：租户级租户内全见 + 用户级仅归属用户")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<PageResult<SkillDTO>> getSkillPage(@Validated SkillPageQuery query) {
        return success(skillService.getSkillPage(query, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/version-page")
    @Operation(summary = "获得 Skill 版本列表", description = "含当前版本标识")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<List<SkillVersionDTO>> listSkillVersions(@RequestParam("skillId") Long skillId) {
        return success(skillService.listSkillVersions(skillId, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/version-get")
    @Operation(summary = "获得 Skill 版本内容", description = "markdown 全文 + 资源路径清单（版本预览用，对齐 agentspec version-get 先例）")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<SkillVersionContentDTO> getVersionContent(
            @RequestParam("skillId") Long skillId, @RequestParam("versionNo") Integer versionNo) {
        return success(skillService.getVersionContent(skillId, versionNo));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 Skill", description = "级联删除版本链")
    @PreAuthorize("@ss.hasPermission('ai:skill:delete')")
    public CommonResult<Boolean> deleteSkill(@RequestParam("id") Long id) {
        skillService.deleteSkill(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "上架 Skill", description = "进入终端技能目录（版本推进不影响上架位）")
    @PreAuthorize("@ss.hasPermission('ai:skill:update')")
    public CommonResult<Boolean> publishSkill(@RequestParam("id") Long id) {
        skillService.publishSkill(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/unpublish")
    @Operation(summary = "下架 Skill", description = "移出终端技能目录")
    @PreAuthorize("@ss.hasPermission('ai:skill:update')")
    public CommonResult<Boolean> unpublishSkill(@RequestParam("id") Long id) {
        skillService.unpublishSkill(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

}
