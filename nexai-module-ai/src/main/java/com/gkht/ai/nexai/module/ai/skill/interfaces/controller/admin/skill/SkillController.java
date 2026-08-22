package com.gkht.ai.nexai.module.ai.skill.interfaces.controller.admin.skill;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillPublishCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillUpdateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;
import com.gkht.ai.nexai.module.ai.skill.application.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 技能管理")
@RestController
@RequestMapping("/ai/skill")
@Validated
public class SkillController {

    @Resource
    private SkillService skillService;

    @PostMapping("/create")
    @Operation(summary = "创建技能", description = "SKILL.md 全文 + 资源文件集创建技能（首个草稿）；front matter 的 name/description 必填")
    @PreAuthorize("@ss.hasPermission('ai:skill:create')")
    public CommonResult<Long> createSkill(@Valid @RequestBody SkillCreateCommand command) {
        return success(skillService.createSkill(command));
    }

    @PutMapping("/update")
    @Operation(summary = "编辑技能", description = "以 SKILL.md 全文覆盖草稿；发布后再编辑即生成新草稿")
    @PreAuthorize("@ss.hasPermission('ai:skill:update')")
    public CommonResult<Boolean> updateSkill(@Valid @RequestBody SkillUpdateCommand command) {
        skillService.updateSkill(command);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布技能", description = "当前草稿固化为不可变新版本，默认版本指针前移；无草稿时报错")
    @PreAuthorize("@ss.hasPermission('ai:skill:publish')")
    public CommonResult<Integer> publishSkill(@Valid @RequestBody SkillPublishCommand command) {
        return success(skillService.publishSkill(command));
    }

    @PutMapping("/switch-default-version")
    @Operation(summary = "切换默认版本", description = "把运行时读取的默认版本切到指定已发布版本（回滚/迭代入口）")
    @PreAuthorize("@ss.hasPermission('ai:skill:update')")
    public CommonResult<Boolean> switchDefaultVersion(@Valid @RequestBody SkillSwitchVersionCommand command) {
        skillService.switchDefaultVersion(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除技能", description = "删除技能及其全部版本")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:skill:delete')")
    public CommonResult<Boolean> deleteSkill(@RequestParam("id") Long id) {
        skillService.deleteSkill(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得技能分页")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<PageResult<SkillDTO>> getSkillPage(@Validated SkillPageQuery query) {
        return success(skillService.getSkillPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得技能详情", description = "含草稿与当前默认版本快照，编辑表单按此预填")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<SkillDetailDTO> getSkill(@RequestParam("id") Long id) {
        return success(skillService.getSkill(id));
    }

    @GetMapping("/version/list")
    @Operation(summary = "获得版本历史", description = "按版本号倒序，含各版本 SKILL.md 与资源快照")
    @Parameter(name = "skillId", description = "技能编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:skill:query')")
    public CommonResult<List<SkillVersionDTO>> getVersionList(@RequestParam("skillId") Long skillId) {
        return success(skillService.getVersionList(skillId));
    }

}
