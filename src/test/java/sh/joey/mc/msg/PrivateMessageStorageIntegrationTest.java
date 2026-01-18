package sh.joey.mc.msg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for PrivateMessageStorage.
 */
class PrivateMessageStorageIntegrationTest extends PostgresIntegrationTest {

    private PrivateMessageStorage messageStorage;

    @BeforeEach
    void setUpStorage() {
        messageStorage = new PrivateMessageStorage(storage);
    }

    @Nested
    @DisplayName("Store and Retrieve Messages")
    class StoreAndRetrieveTests {

        @Test
        @DisplayName("Store and get unread messages")
        void storeAndGetUnreadMessages() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "Hello there!"));

            List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).senderId()).isEqualTo(sender);
            assertThat(messages.get(0).recipientId()).isEqualTo(recipient);
            assertThat(messages.get(0).content()).isEqualTo("Hello there!");
            assertThat(messages.get(0).readAt()).isNull();
            assertThat(messages.get(0).createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Get unread messages returns empty when no messages")
        void getUnreadMessages_noMessages_returnsEmpty() {
            UUID recipient = UUID.randomUUID();

            List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));

            assertThat(messages).isEmpty();
        }

        @Test
        @DisplayName("Multiple messages are ordered by creation time (oldest first)")
        void multipleMessages_orderedByCreationTime() throws InterruptedException {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "First message"));
            Thread.sleep(10); // Ensure different timestamps
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Second message"));
            Thread.sleep(10);
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Third message"));

            List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));

            assertThat(messages).hasSize(3);
            assertThat(messages.get(0).content()).isEqualTo("First message");
            assertThat(messages.get(1).content()).isEqualTo("Second message");
            assertThat(messages.get(2).content()).isEqualTo("Third message");
        }

        @Test
        @DisplayName("Messages from different senders are all retrieved")
        void differentSenders_allRetrieved() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender1, recipient, "From sender 1"));
            blockingAwait(messageStorage.storeMessage(sender2, recipient, "From sender 2"));

            List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));

            assertThat(messages).hasSize(2);
            assertThat(messages).extracting(PrivateMessage::senderId)
                    .containsExactlyInAnyOrder(sender1, sender2);
        }
    }

    @Nested
    @DisplayName("Mark as Read")
    class MarkAsReadTests {

        @Test
        @DisplayName("Mark single message as read")
        void markAsRead_singleMessage() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "Test message"));

            List<PrivateMessage> unread = blockingList(messageStorage.getUnreadMessages(recipient));
            assertThat(unread).hasSize(1);

            blockingAwait(messageStorage.markAsRead(unread.get(0).id()));

            List<PrivateMessage> afterRead = blockingList(messageStorage.getUnreadMessages(recipient));
            assertThat(afterRead).isEmpty();
        }

        @Test
        @DisplayName("Mark all messages as read")
        void markAllAsRead() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 1"));
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 2"));
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 3"));

            blockingAwait(messageStorage.markAllAsRead(recipient));

            List<PrivateMessage> afterRead = blockingList(messageStorage.getUnreadMessages(recipient));
            assertThat(afterRead).isEmpty();
        }

        @Test
        @DisplayName("Mark all as read only affects specified recipient")
        void markAllAsRead_onlyAffectsRecipient() {
            UUID sender = UUID.randomUUID();
            UUID recipient1 = UUID.randomUUID();
            UUID recipient2 = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient1, "To recipient 1"));
            blockingAwait(messageStorage.storeMessage(sender, recipient2, "To recipient 2"));

            blockingAwait(messageStorage.markAllAsRead(recipient1));

            assertThat(blockingList(messageStorage.getUnreadMessages(recipient1))).isEmpty();
            assertThat(blockingList(messageStorage.getUnreadMessages(recipient2))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Count Pending Messages")
    class CountPendingTests {

        @Test
        @DisplayName("Count pending messages from specific sender")
        void countPendingFromSender() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 1"));
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 2"));
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 3"));

            int count = blockingGet(messageStorage.countPendingFromSender(sender, recipient));

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("Count pending returns zero when no messages")
        void countPending_noMessages_returnsZero() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            int count = blockingGet(messageStorage.countPendingFromSender(sender, recipient));

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("Count pending excludes read messages")
        void countPending_excludesReadMessages() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 1"));
            blockingAwait(messageStorage.storeMessage(sender, recipient, "Message 2"));

            List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));
            blockingAwait(messageStorage.markAsRead(messages.get(0).id()));

            int count = blockingGet(messageStorage.countPendingFromSender(sender, recipient));

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Count pending only counts from specified sender")
        void countPending_onlyFromSpecifiedSender() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            blockingAwait(messageStorage.storeMessage(sender1, recipient, "From sender 1"));
            blockingAwait(messageStorage.storeMessage(sender1, recipient, "From sender 1 again"));
            blockingAwait(messageStorage.storeMessage(sender2, recipient, "From sender 2"));

            int countFromSender1 = blockingGet(messageStorage.countPendingFromSender(sender1, recipient));
            int countFromSender2 = blockingGet(messageStorage.countPendingFromSender(sender2, recipient));

            assertThat(countFromSender1).isEqualTo(2);
            assertThat(countFromSender2).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Message ID is auto-generated")
    void messageId_autoGenerated() throws SQLException {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        blockingAwait(messageStorage.storeMessage(sender, recipient, "Test message"));

        List<PrivateMessage> messages = blockingList(messageStorage.getUnreadMessages(recipient));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).id()).isNotNull();
    }
}
