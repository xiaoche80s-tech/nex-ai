package com.gkht.ai.nexai.module.ai.skill.application.command;

import java.util.Map;

/**
 * 技能草稿命令公共契约：创建与编辑命令共有的草稿字段。技能内容以 SKILL.md 全文 +
 * 资源文件集表达（front matter 的 name/description 由服务端解析校验并同步为技能标识）。
 */
public interface SkillDraftCommand {

    String getSkillMd();

    Map<String, String> getResources();

}
