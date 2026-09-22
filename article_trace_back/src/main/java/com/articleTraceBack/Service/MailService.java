package com.articleTraceBack.Service;

/** 邮件投递服务：负责落库与失败重试扫描；实际投递由实现内部提交到线程池异步执行。 */
public interface MailService {

    /** 落库并异步投递一封纯文本邮件；先入库再发送，失败可由重试任务补发。 */
    default void send(String toEmail, String subject, String content) {
        send(toEmail, subject, content, null);
    }

    /** 落库并异步投递一封邮件（可带 HTML 与纯文本两载体）；{@code contentHtml} 为空时退化为纯文本单载体。 */
    void send(String toEmail, String subject, String content, String contentHtml);

    /**
     * 重试所有未超上限的失败邮件（失败次数 ≤ {@code notification.mail.maxRetry}）。
     *
     * @return 本次提交重试的条数
     */
    int retryFailed();
}
