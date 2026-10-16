package com.acanx.module.aha.common.exception;

/**
 * 应用内升级 / 降级相关异常（issue #51）。
 *
 * <p>错误码区分「用户能自己处理」的种类：限流要提示稍后再试，网络要提示检查连接，
 * 内容异常要提示发布页或本地包坏了 —— 界面上不能都渲染成一句「更新失败」。</p>
 *
 * @since 0.2.0
 */
public class UpdateException extends AhaException {

    /** 发布页查询被限流（未认证请求 60 次/时/IP）。 */
    public static final String CODE_RATE_LIMITED = "UPDATE_RATE_LIMITED";

    /** 发布页返回了非 200 的 HTTP 状态。 */
    public static final String CODE_HTTP_ERROR = "UPDATE_HTTP_ERROR";

    /** 网络层失败（连接超时、连接被拒、请求中断）。 */
    public static final String CODE_NETWORK_ERROR = "UPDATE_NETWORK_ERROR";

    /** 返回内容无法解析（不是预期的 JSON 结构，或同一形态/平台匹配到多个资产）。 */
    public static final String CODE_INVALID_RESPONSE = "UPDATE_INVALID_RESPONSE";

    /** 该版本 / 该通道没有本平台本形态的包（界面按 issue #51 §3.3 的 {@code ASSET_MISSING} 处理）。 */
    public static final String CODE_ASSET_MISSING = "UPDATE_ASSET_MISSING";

    /** 本平台不在发布范围内（如 Windows ARM64 —— 打不出包，与「应用内升级还没实现」是两码事）。 */
    public static final String CODE_PLATFORM_UNPUBLISHED = "UPDATE_PLATFORM_UNPUBLISHED";

    /** 下载失败：重试后仍不行、读流中断、写盘失败，或读取已下载文件失败。 */
    public static final String CODE_DOWNLOAD_FAILED = "UPDATE_DOWNLOAD_FAILED";

    /** 下载地址不在白名单（issue #51 §4.7）：只允许 GitHub 发布页与它的资产域。 */
    public static final String CODE_UNTRUSTED_SOURCE = "UPDATE_UNTRUSTED_SOURCE";

    /** 校验和不符：**不落地**（issue #51 §4.3 第①层）。 */
    public static final String CODE_CHECKSUM_MISMATCH = "UPDATE_CHECKSUM_MISMATCH";

    /** 包内清单不合格：缺失、路径非法、大小/哈希不符，或版本与形态对不上（§4.3 第②③层）。 */
    public static final String CODE_PACKAGE_INVALID = "UPDATE_PACKAGE_INVALID";

    /** 用户取消（不是错误：界面不该弹错误框，`.part` 会保留下来当排查现场，重试是重新下载）。 */
    public static final String CODE_CANCELLED = "UPDATE_CANCELLED";

    /**
     * 换装 helper 拉起后**没有自报「已启动」**：它根本没跑起来（典型：安全策略拦下 PowerShell、
     * 启动器把子进程放进随父进程退出的作业对象）。
     *
     * <p>有这一条才能避免「App 关了、什么都没发生」：App 等不到标记就不退出，留在前台报错。</p>
     *
     * @since 0.2.0
     */
    public static final String CODE_HELPER_NOT_STARTED = "UPDATE_HELPER_NOT_STARTED";

    /**
     * 构造异常。
     *
     * @param code    错误码（见本类的常量）
     * @param message 错误信息
     */
    public UpdateException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造异常。
     *
     * @param code    错误码（见本类的常量）
     * @param message 错误信息
     * @param cause   原因
     */
    public UpdateException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 是否属于「限流」：调用方据此给「稍后再试」，而不是让用户以为坏了。
     *
     * @return 限流返回 {@code true}
     */
    public boolean rateLimited() {
        return CODE_RATE_LIMITED.equals(code());
    }
}
