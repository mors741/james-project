/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class NotifyMailetsMessageTest {

    private TimeZone timeZone;

    @Before
    public void setUp() throws Exception {
        timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(timeZone);
    }

    @Test
    public void generateMessageShouldReturnTheMessageWhenSimpleMimeMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .sender(new MailAddress("user", "james.org"))
                .build();

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: user@james.org\n");
    }

    @Test
    public void generateMessageShouldAddErrorMessageWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);
        mail.setErrorMessage("error message");

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Error message below:\n" +
                "error message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n");
    }

    @Test
    public void generateMessageShouldAddSubjectWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("my subject");
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Subject: my subject");
    }

    @Test
    public void generateMessageShouldAddSentDateWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSentDate(DateTime.parse("2016-09-08T14:25:52.000Z").toDate());
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Sent date: Thu Sep 08 14:25:52 UTC 2016");
    }

    @Test
    public void generateMessageShouldAddRecipientsWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);
        mail.setRecipients(ImmutableList.of(new MailAddress("user", "james.org"), new MailAddress("user2", "james.org")));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("RCPT TO: user@james.org\n" +
            "           user2@james.org");
    }

    @Test
    public void generateMessageShouldAddFromWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setFrom(new InternetAddress("user@james.org"));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("From: \n" +
            "user@james.org");
    }

    @Test
    public void generateMessageShouldAddToWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setRecipients(RecipientType.TO, new InternetAddress[] { new InternetAddress("user@james.org"), new InternetAddress("user2@james.org") });
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("To: \n" +
            "user@james.org \n" +
            "user2@james.org");
    }

    @Test
    public void generateMessageShouldAddCCWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setRecipients(RecipientType.CC, new InternetAddress[] { new InternetAddress("user@james.org"), new InternetAddress("user2@james.org") });
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("CC: \n" +
            "user@james.org \n" +
            "user2@james.org");
    }

    @Test
    public void generateMessageShouldAddSizeWhenPossible() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "\r\n" +
                "test\r\n";
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);
        mail.setMessageSize(6);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Size: 6 B");
    }

    @Test
    public void generateMessageShouldSpecifySizeInAReadableWay() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);
        mail.setMessageSize((long)(5.9*1024));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Size: 5.9 KiB");
    }

    @Test
    public void getMessageInternalSizeShouldTransformMessagingErrorIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenThrow(MessagingException.class);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    public void getMessageInternalSizeShouldTransformZeroSizeIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(0L);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    public void getMessageInternalSizeShouldTransformNegativeIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(-1L);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    public void getMessageInternalSizeShouldReturnSizeWhenAvailable() throws MessagingException {
        long size = 42L;

        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(size);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.of(size));
    }

    @Test
    public void generateMessageShouldDecodeEncodedSubject() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Subject: =?UTF-8?Q?Cl=c3=b4ture_&_Paie_du_mois?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Subject: Clôture & Paie du mois");
    }

    @Test
    public void generateMessageShouldDecodeEncodedFrom() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "From: =?UTF-8?Q?=F0=9F=90=83@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  From: \n" +
            "🐃@linagora.com");
    }

    @Test
    public void generateMessageShouldDecodeEncodedTo() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "To: =?UTF-8?Q?=F0=9F=9A=BE@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  To: \n" +
            "🚾@linagora.com");
    }

    @Test
    public void generateMessageShouldDecodeEncodedCc() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Cc: =?UTF-8?Q?=F0=9F=9A=B2@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  CC: \n" +
            "🚲@linagora.com");
    }

    @Test
    public void safelyDecodeShouldReturnTextNotEncodedUnmodified() throws Exception {
        String text = "Why not unicode for Llama";

        assertThat(NotifyMailetsMessage.safelyDecode(text))
            .isEqualTo(text);
    }

    @Test
    public void safelyDecodeShouldCorrectlyDecodeQuotedPrintable() throws Exception {
        assertThat(NotifyMailetsMessage.safelyDecode("=?UTF-8?Q?=E2=99=A5=F0=9F=9A=B2?="))
            .isEqualTo("♥🚲");
    }

    @Test
    public void safelyDecodeShouldReturnInvalidEncodedTextUnmodified() throws Exception {
        String invalidEncodedText = "=?UTF-8?Q?=E2=99=A5=FX=9F=9A=B2?=";

        assertThat(NotifyMailetsMessage.safelyDecode(invalidEncodedText))
            .isEqualTo(invalidEncodedText);
    }

    @Test
    public void safelyDecodeShouldReturnEncodedTextUnmodifiedWhenUnknownCharset() throws Exception {
        String encodedTextWithUnknownCharset = "=?UTF-9?Q?=E2=99=A5=F0=9F=9A=B2?=";

        assertThat(NotifyMailetsMessage.safelyDecode(encodedTextWithUnknownCharset))
            .isEqualTo(encodedTextWithUnknownCharset);
    }
}
