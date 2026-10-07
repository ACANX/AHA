package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.task.TaskRequest;
import com.acanx.module.aha.core.task.TaskResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 服务门面。
 *
 * <p>本地、远程、WSL、容器、云函数等形态统一实现本接口；
 * 上层（CLI / 调度器 / 协议适配器）通过 {@link AgentServiceFactory} 获取实例。</p>
 *
 * @since 0.1.0
 */
public interface AgentService {

    /**
     * 创建会话。
     *
     * @param config 会话配置
     * @return 会话 ID
     */
    String createSession(SessionConfig config);

    /**
     * 关闭会话。
     *
     * <p>语义：结束该会话的活跃状态并释放资源。会话配置与消息已持久化，
     * 因此在 0.1 实现中记录不会被删除，仍可通过相同 ID 再次访问
     * （{@link #chat} / {@link #streamChat} 会从存储恢复会话配置）。</p>
     *
     * @param sessionId 会话 ID
     */
    void closeSession(String sessionId);

    /**
     * 非流式对话。
     *
     * @param sessionId 会话 ID
     * @param input     输入
     * @return 响应
     */
    CompletableFuture<AgentResponse> chat(String sessionId, String input);

    /**
     * 流式对话。
     *
     * @param sessionId 会话 ID
     * @param input     输入
     * @param listener  事件监听器
     */
    void streamChat(String sessionId, String input, AgentEventListener listener);

    /**
     * 流式对话（可取消）。
     *
     * <p>取消语义由实现决定，但必须保证：取消后**已生成的部分内容仍会落库**，
     * 否则用户看到的内容与下一轮模型看到的历史会不一致。</p>
     *
     * @param sessionId 会话 ID
     * @param input     用户输入
     * @param listener  事件监听器
     * @param token     取消令牌；{@code null} 时退化为不可取消
     */
    default void streamChat(String sessionId, String input, AgentEventListener listener,
                            CancellationToken token) {
        streamChat(sessionId, input, listener);
    }

    /**
     * 列出可用工具。
     *
     * @return 工具描述符
     */
    List<ToolDescriptor> listTools();

    /**
     * 调用工具。
     *
     * @param toolName 工具名
     * @param params   参数
     * @return 结果
     */
    ToolResult invokeTool(String toolName, Map<String, Object> params);

    /**
     * 调用工具，可选择用户已显式确认。
     *
     * <p>当 {@code userConfirmed} 为 {@code true} 时，跳过本地权限策略校验，
     * 用于 CLI 等交互场景中用户对 WRITE/EXECUTE/ADMIN 工具的手动放行。</p>
     *
     * @param toolName      工具名
     * @param params        参数
     * @param userConfirmed 用户是否已显式确认
     * @return 结果
     */
    default ToolResult invokeTool(String toolName, Map<String, Object> params, boolean userConfirmed) {
        return invokeTool(toolName, params);
    }

    /**
     * 检索记忆。
     *
     * @param sessionId 会话 ID
     * @param query     查询
     * @param limit     数量上限
     * @return 记忆条目
     */
    List<MemoryEntry> recall(String sessionId, String query, int limit);

    /**
     * 运行时描述符。
     *
     * <p>默认返回本机运行时；远程 / WSL / 容器 / 云函数实现必须覆写。</p>
     *
     * @return 描述符
     */
    default RuntimeDescriptor runtime() {
        return RuntimeDescriptor.local();
    }

    /**
     * 提交任务（统一入口）。
     *
     * <p>调度器、协议适配器、云函数入口与消息队列消费者均通过本方法下发任务，
     * 无需感知会话创建与异常包装细节。默认实现基于 {@link #chat}：</p>
     * <ul>
     *   <li>{@code request.sessionId()} 为空时自动创建会话，任务结束后关闭</li>
     *   <li>异常统一转换为 {@link TaskResult#failure}，不向上抛出</li>
     * </ul>
     *
     * @param request 任务请求
     * @param token   取消令牌
     * @return 任务结果
     */
    default TaskResult submit(TaskRequest request, CancellationToken token) {
        boolean ownsSession = request.sessionId() == null || request.sessionId().isBlank();
        String sessionId = request.sessionId();
        long start = System.currentTimeMillis();
        try {
            if (ownsSession) {
                sessionId = createSession(new SessionConfig(
                        request.model(), request.systemPrompt(), request.context()));
            }
            AgentResponse response = chat(sessionId, request.input()).join();
            List<ToolCall> calls = response.toolCalls() == null ? List.of() : response.toolCalls();
            return new TaskResult(
                    request.id(),
                    true,
                    response.content(),
                    null,
                    null,
                    calls.stream().map(ToolCall::name).toList(),
                    System.currentTimeMillis() - start,
                    Map.of("SessionId", sessionId, "Runtime", runtime().id()));
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String code = cause instanceof AhaException aha ? aha.code() : "TASK_FAILED";
            return TaskResult.failure(request.id(), code, cause.getMessage());
        } finally {
            if (ownsSession && sessionId != null) {
                closeSession(sessionId);
            }
        }
    }

    /**
     * 关闭服务。
     */
    void shutdown();
}
