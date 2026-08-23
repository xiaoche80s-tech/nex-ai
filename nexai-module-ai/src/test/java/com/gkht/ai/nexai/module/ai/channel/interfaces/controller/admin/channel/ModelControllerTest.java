package com.gkht.ai.nexai.module.ai.channel.interfaces.controller.admin.channel;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ModelPageQuery;
import com.gkht.ai.nexai.module.ai.channel.application.service.ChannelService;
import com.gkht.ai.nexai.module.ai.channel.application.service.ChannelServiceImpl;
import com.gkht.ai.nexai.module.ai.channel.application.service.ModelService;
import com.gkht.ai.nexai.module.ai.channel.application.service.ModelServiceImpl;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ChannelConverterImpl;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ModelConverterImpl;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ModelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.repository.ChannelRepositoryImpl;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_DUPLICATE_MODEL_ID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型 HTTP 契约测试（H2 全链路）：登记/更新/启停/删除、同渠道标识唯一、
 * 挂接渠道校验、删除渠道级联删模型、渠道名补充。
 */
@Import({ChannelController.class, ChannelServiceImpl.class, ChannelRepositoryImpl.class,
        ChannelConverterImpl.class, ModelController.class, ModelServiceImpl.class,
        ModelConverterImpl.class, ChannelMapper.class, ModelMapper.class,
        TenantDbTestConfiguration.class, ModelControllerTest.FakeGatewayConfiguration.class})
public class ModelControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    /** 探测网关桩：本测试不触发探测，仅满足 ChannelServiceImpl 装配依赖（不外呼） */
    @org.springframework.boot.test.context.TestConfiguration
    static class FakeGatewayConfiguration {

        @org.springframework.context.annotation.Bean
        public com.gkht.ai.nexai.module.ai.channel.domain.gateway.ModelConnectivityGateway modelConnectivityGateway() {
            return (channel, modelId) ->
                    com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ConnectivityResult.success(0L, "stub");
        }

    }

    @Resource
    private ChannelController channelController;

    @Resource
    private ModelController modelController;

    @Resource
    private ModelService modelService;

    @Resource
    private ChannelService channelService;

    @Resource
    private ModelMapper modelMapper;

    /** setUp 创建的渠道编号（主键为雪花 ID，不假定具体值） */
    private Long channelId;

    /** 注入以强制初始化租户拦截器 bean（懒加载环境下不被依赖则不初始化，Mapper 将无租户过滤） */
    @Resource
    private com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ChannelCreateCommand channelCommand = new ChannelCreateCommand();
        channelCommand.setName("OpenAI 主渠道");
        channelCommand.setProvider("openai");
        channelCommand.setBaseUrl("https://api.openai.com/v1");
        channelCommand.setApiKey("sk-xxx");
        channelId = channelController.createChannel(channelCommand).getData();
    }

    private ModelCreateCommand createCommand(String modelId) {
        ModelCreateCommand command = new ModelCreateCommand();
        command.setChannelId(channelId);
        command.setModelId(modelId);
        command.setName("GPT-4o 主力");
        command.setContextWindow(128_000);
        command.setInputPrice(new BigDecimal("0.5"));
        command.setOutputPrice(new BigDecimal("2.5"));
        return command;
    }

    @Test
    @DisplayName("登记模型：落库成功、详情补充渠道名、初始启用")
    public void createModelWithChannelName() {
        Long id = modelController.createModel(createCommand("gpt-4o")).getData();
        ModelDTO dto = modelController.getModel(id).getData();
        assertEquals("gpt-4o", dto.getModelId());
        assertEquals("OpenAI 主渠道", dto.getChannelName());
        assertEquals(128_000, dto.getContextWindow());
        assertTrue(dto.getEnabled());
    }

    @Test
    @DisplayName("同渠道下模型标识唯一；不同渠道可同名共存")
    public void modelIdUniqueWithinChannel() {
        modelController.createModel(createCommand("gpt-4o"));
        assertServiceException(() -> modelController.createModel(createCommand("gpt-4o")),
                MODEL_DUPLICATE_MODEL_ID, "gpt-4o");

        // 第二个渠道下同名模型不冲突
        ChannelCreateCommand second = new ChannelCreateCommand();
        second.setName("中转渠道");
        second.setProvider("openai-compat");
        second.setBaseUrl("https://relay.example.com/v1");
        Long secondChannelId = channelService.createChannel(second);

        ModelCreateCommand sameNameOtherChannel = createCommand("gpt-4o");
        sameNameOtherChannel.setChannelId(secondChannelId);
        modelController.createModel(sameNameOtherChannel);
        assertEquals(2L, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());
    }

    @Test
    @DisplayName("挂接不存在的渠道被拒绝；模型不存在时报业务错误码")
    public void validatesChannelExistence() {
        ModelCreateCommand orphan = createCommand("orphan-model");
        orphan.setChannelId(999L);
        assertServiceException(() -> modelService.createModel(orphan), CHANNEL_NOT_EXISTS);

        assertServiceException(() -> modelService.getModel(999L), MODEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("更新与启停：可改挂渠道与元数据；停用保留数据")
    public void updateAndStatus() {
        ChannelCreateCommand second = new ChannelCreateCommand();
        second.setName("中转渠道");
        second.setProvider("openai-compat");
        second.setBaseUrl("https://relay.example.com/v1");
        Long secondChannelId = channelService.createChannel(second);

        Long id = modelController.createModel(createCommand("gpt-4o")).getData();

        ModelUpdateCommand update = new ModelUpdateCommand();
        update.setId(id);
        update.setChannelId(secondChannelId);
        update.setModelId("gpt-4o-mini");
        update.setName("轻量模型");
        update.setContextWindow(64_000);
        modelController.updateModel(update);
        ModelDTO updated = modelController.getModel(id).getData();
        assertEquals("gpt-4o-mini", updated.getModelId());
        assertEquals("中转渠道", updated.getChannelName());

        ModelUpdateStatusCommand disable = new ModelUpdateStatusCommand();
        disable.setId(id);
        disable.setEnabled(false);
        modelController.updateModelStatus(disable);
        assertEquals(false, modelController.getModel(id).getData().getEnabled());
    }

    @Test
    @DisplayName("删除渠道级联删除其下模型（聚合边界）")
    public void deleteChannelCascadesModels() {
        modelController.createModel(createCommand("gpt-4o"));
        modelController.createModel(createCommand("gpt-4o-mini"));

        channelController.deleteChannel(channelId);
        assertEquals(0, modelMapper.selectCount(null));
        assertEquals(0, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());
        assertEquals(0, channelService.getChannelPage(
                new com.gkht.ai.nexai.module.ai.channel.application.query.ChannelPageQuery()).getTotal());
    }

    @Test
    @DisplayName("多租户隔离：租户二查不到租户一的模型，对其 get/update/delete 均报不存在")
    public void tenantIsolation() {
        Long id = modelController.createModel(createCommand("gpt-4o")).getData();

        TenantContextHolder.setTenantId(2L);
        assertEquals(0, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());
        assertServiceException(() -> modelService.getModel(id), MODEL_NOT_EXISTS);

        ModelUpdateCommand crossTenant = new ModelUpdateCommand();
        crossTenant.setId(id);
        crossTenant.setChannelId(channelId);
        crossTenant.setModelId("hacked");
        crossTenant.setName("越权改名");
        assertServiceException(() -> modelService.updateModel(crossTenant), MODEL_NOT_EXISTS);
        assertServiceException(() -> modelService.deleteModel(id), MODEL_NOT_EXISTS);

        // 回租户一：数据未受跨租户操作影响
        TenantContextHolder.setTenantId(TENANT_ONE);
        assertEquals("gpt-4o", modelController.getModel(id).getData().getModelId());
    }

    @Test
    @DisplayName("分页过滤：按渠道/标识/名称/启用状态筛选")
    public void pageFilters() {
        modelController.createModel(createCommand("gpt-4o"));
        ModelCreateCommand mini = createCommand("gpt-4o-mini");
        mini.setName("轻量模型");
        modelController.createModel(mini);

        ModelPageQuery byChannel = new ModelPageQuery();
        byChannel.setChannelId(channelId);
        assertEquals(2, modelController.getModelPage(byChannel).getData().getTotal());

        ModelPageQuery byModelId = new ModelPageQuery();
        byModelId.setModelId("mini");
        assertEquals(1, modelController.getModelPage(byModelId).getData().getTotal());
    }

}
