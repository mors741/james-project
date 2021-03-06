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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO.MessageIdAttachmentIds;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CassandraMessageDAOTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);
    private static final List<MessageAttachment> NO_ATTACHMENT = ImmutableList.of();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private CassandraCluster cassandra;

    private CassandraMessageDAO testee;
    private CassandraMessageId.Factory messageIdFactory;

    private SimpleMailboxMessage message;
    private CassandraMessageId messageId;
    private List<ComposedMessageIdWithMetaData> messageIds;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraModuleComposite(new CassandraMessageModule(), new CassandraBlobModule()), cassandraServer.getIp(), cassandraServer.getBindingPort());
        messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        testee = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), blobsDAO, CassandraUtils.WITH_DEFAULT_CONFIGURATION, new CassandraMessageId.Factory());

        messageIds = ImmutableList.of(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(MAILBOX_ID, messageId, messageUid))
                .flags(new Flags())
                .modSeq(1)
                .build());
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    @Test
    public void saveShouldSaveNullValueForTextualLineCountAsZero() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount())
            .isEqualTo(0L);
    }

    @Test
    public void saveShouldSaveTextualLineCount() throws Exception {
        long textualLineCount = 10L;
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        message = createMessage(messageId, CONTENT, BODY_START, propertyBuilder, NO_ATTACHMENT);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    public void saveShouldStoreMessageWithFullContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Full, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT);
    }

    @Test
    public void saveShouldStoreMessageWithBodyContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Body, Limit.unlimited()));

        byte[] expected = Bytes.concat(
            new byte[BODY_START],
            CONTENT.substring(BODY_START).getBytes(StandardCharsets.UTF_8));
        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(IOUtils.toString(new ByteArrayInputStream(expected), StandardCharsets.UTF_8));
    }

    @Test
    public void saveShouldStoreMessageWithHeaderContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Headers, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT.substring(0, BODY_START));
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder, Collection<MessageAttachment> attachments) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new SharedByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .propertyBuilder(propertyBuilder)
            .addAttachments(attachments)
            .build();
    }

    private MessageWithoutAttachment toMessage(CompletableFuture<Stream<CassandraMessageDAO.MessageResult>> readOptional) throws InterruptedException, java.util.concurrent.ExecutionException {
        return readOptional.join()
            .map(CassandraMessageDAO.MessageResult::message)
            .map(Pair::getLeft)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("Collection is not supposed to be empty"));
    }

    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldReturnEmptyWhenNone() {
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        assertThat(actual).isEmpty();
    }

    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldReturnOneWhenStored() throws Exception {
        //Given
        MessageAttachment attachment = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment));
        testee.save(message1).join();
        MessageIdAttachmentIds expected = new MessageIdAttachmentIds(messageId, ImmutableSet.of(attachment.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        //Then
        assertThat(actual).containsOnly(expected);
    }

    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldReturnOneWhenStoredWithTwoAttachments() throws Exception {
        //Given
        MessageAttachment attachment1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachment2 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment1, attachment2));
        testee.save(message1).join();
        MessageIdAttachmentIds expected = new MessageIdAttachmentIds(messageId, ImmutableSet.of(attachment1.getAttachmentId(), attachment2.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        //Then
        assertThat(actual).containsOnly(expected);
    }
    
    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldReturnAllWhenStoredWithAttachment() throws Exception {
        //Given
        MessageId messageId1 = messageIdFactory.generate();
        MessageId messageId2 = messageIdFactory.generate();
        MessageAttachment attachment1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachment2 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId1, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment1));
        SimpleMailboxMessage message2 = createMessage(messageId2, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment2));
        testee.save(message1).join();
        testee.save(message2).join();
        MessageIdAttachmentIds expected1 = new MessageIdAttachmentIds(messageId1, ImmutableSet.of(attachment1.getAttachmentId()));
        MessageIdAttachmentIds expected2 = new MessageIdAttachmentIds(messageId2, ImmutableSet.of(attachment2.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        //Then
        assertThat(actual).containsOnly(expected1, expected2);
    }
    
    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldReturnEmtpyWhenStoredWithoutAttachment() throws Exception {
        //Given
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);
        testee.save(message1).join();
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        //Then
        assertThat(actual).isEmpty();
    }
    
    @Test
    public void retrieveAllMessageIdAttachmentIdsShouldFilterMessagesWithoutAttachment() throws Exception {
        //Given
        MessageId messageId1 = messageIdFactory.generate();
        MessageId messageId2 = messageIdFactory.generate();
        MessageId messageId3 = messageIdFactory.generate();
        MessageAttachment attachmentFor1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachmentFor3 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId1, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachmentFor1));
        SimpleMailboxMessage message2 = createMessage(messageId2, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);
        SimpleMailboxMessage message3 = createMessage(messageId3, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachmentFor3));
        testee.save(message1).join();
        testee.save(message2).join();
        testee.save(message3).join();
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().join();
        
        //Then
        assertThat(actual).extracting(MessageIdAttachmentIds::getMessageId)
            .containsOnly(messageId1, messageId3);
    }

    @Test
    public void messageIdAttachmentIdsShouldMatchBeanContract() {
        EqualsVerifier.forClass(MessageIdAttachmentIds.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void messageIdAttachmentIdsShouldThrowOnNullMessageId() {
        assertThatThrownBy(() -> new MessageIdAttachmentIds(null, ImmutableSet.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void messageIdAttachmentIdsShouldThrowOnNullAttachmentIds() {
        assertThatThrownBy(() -> new MessageIdAttachmentIds(messageIdFactory.generate(), null))
            .isInstanceOf(NullPointerException.class);
    }
}
