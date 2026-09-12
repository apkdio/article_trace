package com.articleTraceBack.Service;

/**
 * 邮件投递服务：负责落库与失败重试扫描。
 *
 * <p>投递本身由 {@link MailDeliverer} 异步完成（独立 bean 才能被 {@code @Async} 代理）。</p>
 */
public interface MailService {

    /**
     * 落库并异步投递一封邮件。
     *
     * <p>先入库再发送：即使异步任务失败或队列满被拒，记录仍在，可由重试任务补发。</p>
     */
    void send(String toEmail, String subject, String content);

    /**
     * 重试所有未超上限的失败邮件（失败次数 ≤ {@code notification.mail.maxRetry}）。
     *
     * @return 本次提交重试的条数
     */
    int retryFailed();
}
