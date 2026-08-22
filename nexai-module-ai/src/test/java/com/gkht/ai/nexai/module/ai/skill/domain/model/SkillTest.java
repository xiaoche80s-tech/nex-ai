package com.gkht.ai.nexai.module.ai.skill.domain.model;

import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionImmutableException;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionNotExistsException;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能聚合 S3 纯 JUnit 测试：版本链状态机与内容值对象校验（零框架依赖）。
 */
public class SkillTest {

    private static final String VALID_MD = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\n按模板生成汇报文档。";

    private SkillContent content(String skillMd) {
        return SkillContent.of(skillMd, Map.of("scripts/run.py", "print('hi')"));
    }

    /** 模拟聚合已落库（reconstitute 回带编号），发布语义要求版本归属技能编号 */
    private Skill reconstitutePersisted(Skill created) {
        return Skill.reconstitute(1L, created.getName(), created.getDescription(),
                0, null, created.getDraft(), null);
    }

    @Test
    @DisplayName("创建技能：携带首个草稿，未发布（latest=0、current=null）")
    public void createWithDraft() {
        Skill skill = Skill.create("pdf-report", "生成 PDF 汇报文档", content(VALID_MD));

        assertEquals("pdf-report", skill.getName());
        assertEquals(0, skill.getLatestVersionNo());
        assertNull(skill.getCurrentVersionNo());
        assertTrue(skill.hasDraft());
        assertEquals(VALID_MD, skill.getDraft().getSkillMd());
        assertEquals(Map.of("scripts/run.py", "print('hi')"), skill.getDraft().getResources());
    }

    @Test
    @DisplayName("名称与描述校验：空白/超长/路径字符拒绝；描述必填")
    public void validateProfile() {
        SkillContent draft = content(VALID_MD);
        assertThrows(IllegalArgumentException.class, () -> Skill.create(" ", "描述", draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("a".repeat(65), "描述", draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("bad/name", "描述", draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("bad..name", "描述", draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("ok", " ", draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("ok", "d".repeat(513), draft));
        assertThrows(IllegalArgumentException.class, () -> Skill.create("ok", "描述", null));
    }

    @Test
    @DisplayName("资源路径安全：拒绝 .. 段、反斜杠、空路径；内容不可变")
    public void validateResourcePaths() {
        Map<String, String> traversal = Map.of("scripts/../../etc/passwd", "x");
        assertThrows(IllegalArgumentException.class, () -> SkillContent.of(VALID_MD, traversal));
        Map<String, String> backslash = Map.of("scripts\\run.py", "x");
        assertThrows(IllegalArgumentException.class, () -> SkillContent.of(VALID_MD, backslash));
        Map<String, String> blankPath = new LinkedHashMap<>();
        blankPath.put(" ", "x");
        assertThrows(IllegalArgumentException.class, () -> SkillContent.of(VALID_MD, blankPath));
        assertThrows(Exception.class, () ->
                SkillContent.of(VALID_MD, Map.of("ok.py", "x")).getResources().put("evil.py", "y"));
        assertThrows(IllegalArgumentException.class, () -> SkillContent.of(" ", Map.of()));
    }

    @Test
    @DisplayName("内容值对象按值判等")
    public void contentValueEquality() {
        assertEquals(SkillContent.of(VALID_MD, Map.of("a.txt", "1")), SkillContent.of(VALID_MD, Map.of("a.txt", "1")));
        assertNotEquals(SkillContent.of(VALID_MD, Map.of()), SkillContent.of(VALID_MD, Map.of("a.txt", "1")));
    }

    @Test
    @DisplayName("发布状态机：草稿固化为 v1、指针前移、草稿清空；再编辑生成新草稿；v2 后 v1 不变")
    public void publishStateMachine() {
        // 发布要求聚合已落库（版本归属技能编号），未落库聚合拒绝
        Skill detached = Skill.create("pdf-report", "生成 PDF 汇报文档", content(VALID_MD));
        assertThrows(IllegalArgumentException.class, () -> detached.publish(null));

        Skill skill = reconstitutePersisted(detached);
        SkillVersion v1 = skill.publish("首个版本");
        assertEquals(1, v1.getVersionNo());
        assertEquals(1, skill.getLatestVersionNo());
        assertEquals(1, skill.getCurrentVersionNo());
        assertFalse(skill.hasDraft());

        // 再编辑 → 新草稿 → 发布 v2
        String updatedMd = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档 v2\n---\n按新模板生成。";
        skill.editDraft("pdf-report", "生成 PDF 汇报文档 v2", content(updatedMd));
        assertTrue(skill.hasDraft());
        SkillVersion v2 = skill.publish("v2");
        assertEquals(2, v2.getVersionNo());
        assertEquals(2, skill.getCurrentVersionNo());
        // v1 快照仍是发布时内容（不可变）
        assertEquals(VALID_MD, v1.getContent().getSkillMd());
        assertEquals("首个版本", v1.getRemark());
    }

    @Test
    @DisplayName("无草稿发布拒绝；版本不可变守护；越界切换默认版本拒绝")
    public void publishWithoutDraftAndImmutability() {
        Skill skill = reconstitutePersisted(
                Skill.create("pdf-report", "生成 PDF 汇报文档", content(VALID_MD)));
        skill.publish(null);
        assertThrows(SkillPublishWithoutDraftException.class, () -> skill.publish(null));

        SkillVersion version = SkillVersion.reconstitute(1L, 1L, 1, content(VALID_MD), null, null);
        assertThrows(SkillVersionImmutableException.class, version::modify);

        assertThrows(SkillVersionNotExistsException.class, () -> skill.switchDefaultVersion(2));
        skill.switchDefaultVersion(1);
        assertEquals(1, skill.getCurrentVersionNo());
    }

    @Test
    @DisplayName("版本按（技能 + 版本号）判等；聚合根按编号判等")
    public void equality() {
        SkillVersion v1a = SkillVersion.reconstitute(1L, 1L, 1, content(VALID_MD), null, null);
        SkillVersion v1b = SkillVersion.reconstitute(2L, 1L, 1, content("other"), "r", null);
        assertEquals(v1a, v1b);

        Skill a = Skill.reconstitute(1L, "n", "d", 0, null, content(VALID_MD), null);
        Skill b = Skill.reconstitute(1L, "other", "d2", 2, 2, null, null);
        assertEquals(a, b);
        Skill c = Skill.create("n", "d", content(VALID_MD));
        assertNotEquals(a, c);
    }

}
