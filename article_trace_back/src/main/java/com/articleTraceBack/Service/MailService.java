package com.articleTraceBack.Service;

/**
 * 邮件投递服务：负责落库与失败重试扫描。
 *
 * <p>投递由实现内部提交到邮件线程池异步执行，调用方无需关心。</p>
 */
public interface MailService {

    /**
     * 落库并异步投递一封纯文本邮件。
     *
     * <p>先入库再发送：即使异步任务失败或队列满被拒，记录仍在，可由重试任务补发。</p>
     */
    default void send(String toEmail, String subject, String content) {
        send(toEmail, subject, content, null);
    }

    /**
     * 落库并异步投递一封邮件，可同时携带 HTML 与纯文本两种载体。
     *
     * <p>{@code contentHtml} 为 null 或空白时退化为纯文本单载体；
     * 两者都有时按 {@code multipart/alternative} 投递，由邮件客户端择一显示。</p>
     */
    void send(String toEmail, String subject, String content, String contentHtml);

    /**
     * 重试所有未超上限的失败邮件（失败次数 ≤ {@code notification.mail.maxRetry}）。
     *
     * @return 本次提交重试的条数
     */
    int retryFailed();
}
