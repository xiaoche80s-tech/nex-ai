package com.gkht.ai.nexai.module.ai.shared.tool;

import java.util.List;

/**
 * 平台工具库条目端口（shared 扩展点，工单 13 定案）：平台业务工具经 agentscope
 * {@code @Tool} 注解 POJO 注册进平台工具库，供规格经 ToolMount（source=PLATFORM）挂载引用。
 *
 * <p><b>sourceId 语义（本票定案）</b>：ToolMount.sourceId = 条目的稳定注册编号
 * {@link #getId()}——代码内声明、不落库（平台工具随部署版本演进，非租户数据）；
 * 部署版本间条目增删时，悬空引用在装配期降级跳过并记 warn。</p>
 */
public interface PlatformToolEntry {

    /** 稳定注册编号（挂载引用 ToolMount.sourceId 的语义，全局唯一） */
    Long getId();

    /** 条目标识（slug） */
    String getCode();

    /** 显示名 */
    String getName();

    /** 条目说明（挂载编辑面展示） */
    String getDescription();

    /** 条目提供的工具名集合（@Tool 注解方法名；挂载白名单收敛的候选面） */
    List<String> getToolNames();

    /** 含 @Tool 注解方法的工具实例（注册进 agentscope Toolkit） */
    Object getToolInstance();

}
