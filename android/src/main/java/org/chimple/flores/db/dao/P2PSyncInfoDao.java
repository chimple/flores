package org.chimple.flores.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.entity.P2PLatestInfoByUserAndDevice;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.db.entity.P2PUserIdDeviceId;


@Dao
public interface P2PSyncInfoDao {

    @Query("SELECT * FROM P2PSyncInfo where message_type != 'missing' order by logged_at asc")
    public P2PSyncInfo[] refreshAllMessages();


    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId")
    public P2PSyncInfo[] getSyncInformationByUserIdAndDeviceId(String schoolId, String userId, String deviceId);

    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId")
    public P2PSyncInfo[] getSyncInformationByUserId(String schoolId, String userId);

    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId and message_type= :messageType")
    public P2PSyncInfo getProfileByUserId(String schoolId, String userId, String messageType);


    @Query("SELECT MAX(sequence) FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId GROUP BY user_id, device_id")
    public Long getLatestSequenceAvailableByUserIdAndDeviceId(String schoolId, String userId, String deviceId);

    @Query("SELECT MAX(step) FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND session_id=:sessionId GROUP BY user_id, session_id")
    public Long getLatestStepForUserIdAndSessionId(String schoolId, String userId, String sessionId);


    @Query("SELECT school_id, user_id, device_id, sequence as sequence FROM P2PSyncInfo where school_id=:schoolId AND user_id=:userId AND device_id=:deviceId and message_type = 'missing' order by sequence asc")
    public P2PLatestInfoByUserAndDevice[] getMissingMessagesByUserIdAndDeviceId(String schoolId, String userId, String deviceId);

    @Query("SELECT user_id, device_id, MAX(sequence) as sequence FROM P2PSyncInfo where school_id=:schoolId AND user_id is not null and device_id is not null and message is not null and message_type != 'missing' GROUP BY user_id, device_id")
    public P2PLatestInfoByUserAndDevice[] getLatestInfoAvailableByUserIdAndDeviceId(String schoolId);


    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId AND sequence >= :startingSequence and sequence <= :endingSequence")
    public P2PSyncInfo[] fetchByUserAndDeviceBetweenSequences(String schoolId, String userId, String deviceId, Long startingSequence, Long endingSequence);


    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId AND sequence <= :sequence")
    public P2PSyncInfo[] fetchByUserAndDeviceUpToSequence(String schoolId, String userId, String deviceId, Long sequence);

    @Query("SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId AND sequence = :sequence")
    public P2PSyncInfo fetchByUserAndDeviceAndSequence(String schoolId, String userId, String deviceId, Long sequence);


    @Query("SELECT id FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id = :userId AND device_id = :deviceId AND sequence = :sequence")
    public Long findId(String schoolId, String userId, String deviceId, Long sequence);


    @Query("SELECT max(sequence) FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId and sequence < :sequence and message_type != 'missing' and message is not null GROUP BY user_id, device_id")
    public Long fetchMinValidSequenceByUserAndDevice(String schoolId, String userId, String deviceId, Long sequence);

    @Query("DELETE FROM P2PSyncInfo WHERE device_id = :deviceId")
    public void deletePerDeviceID(String deviceId);

    @Query("DELETE FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId and sequence = :sequence")
    public void deleteMessage(String schoolId, String userId, String deviceId, Long sequence);


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public Long insertP2PSyncInfo(P2PSyncInfo info);

    @Query("SELECT ps.device_id from (SELECT user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE message_type = 'Photo' group by user_id) as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id  and ps.sequence = tmp.sequence and ps.school_id =:schoolId and ps.user_id =:userId")
    public String getDeviceForRecipientUserId(String schoolId, String userId);


    @Query("SELECT ps.school_id, ps.user_id, ps.device_id, ps.message  from (SELECT school_id, user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE school_id=:schoolId AND message_type = 'Photo' group by user_id) as tmp, P2PSyncInfo ps where ps.school_id =:schoolId AND ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public P2PUserIdDeviceIdAndMessage[] fetchAllUsers(String schoolId);

    @Query("SELECT MAX(step) FROM P2PSyncInfo WHERE school_id=:schoolId AND session_id=:sessionId GROUP BY session_id")
    public Long getLatestStepSessionId(String schoolId, String sessionId);

    @Query("SELECT tmp.school_id, tmp.user_id, ps.message from (SELECT school_id, user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE school_id=:schoolId AND message_type = :messageType AND user_id in (:userIds) group by user_id)  as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String schoolId, String messageType, List<String> userIds);

    @Query("SELECT tmp.school_id, tmp.user_id, ps.message from (SELECT school_id, user_id, max(sequence) as sequence FROM P2PSyncInfo  WHERE school_id=:schoolId AND message_type = :messageType group by user_id)  as tmp, P2PSyncInfo ps where ps.user_id = tmp.user_id and ps.sequence = tmp.sequence")
    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String schoolId, String messageType);


    @Query("SELECT * FROM (SELECT * FROM P2PSyncInfo WHERE school_id=:schoolId AND message_type = :messageType AND ((user_id = :userId and recipient_user_id = :recipientId) or (user_id = :recipientId and recipient_user_id = :userId)) order by created_at desc limit 100) order by created_at asc")
    public List<P2PSyncInfo> fetchConversations(String schoolId, String userId, String recipientId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where school_id=:schoolId AND message_type = :messageType group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.status = 1 and ((p2p.user_id = :userId and p2p.recipient_user_id = :recipientId) or (p2p.user_id = :recipientId and p2p.recipient_user_id = :userId))")
    public List<P2PSyncInfo> fetchLatestConversations(String schoolId, String userId, String recipientId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where school_id=:schoolId AND message_type = :messageType group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.status = 1 and (p2p.user_id = :userId or p2p.recipient_user_id = :userId)")
    public List<P2PSyncInfo> fetchLatestConversations(String schoolId, String userId, String messageType);

    @Query("SELECT p2p.* from (SELECT session_id, max(step) as step from P2PSyncInfo where status = 1 group by session_id) tmp, P2PSyncInfo p2p where p2p.session_id = tmp.session_id and p2p.step = tmp.step and p2p.school_id =:schoolId and p2p.user_id = :userId")
    public List<P2PSyncInfo> fetchLatestConversationsByUser(String schoolId, String userId);

    @Query("SELECT max(sequence) FROM P2PSyncInfo WHERE school_id=:schoolId AND user_id=:userId AND device_id=:deviceId and message_type='Photo'")
    public Long findLatestProfilePhotoId(String schoolId, String userId, String deviceId);

    @Query("select school_id, user_id, device_id from P2PSyncInfo where school_id=:schoolId AND message_type is not null and message_type != 'Photo' group by sender having count(*) > :purgeLimit")
    public List<P2PUserIdDeviceId> findSenderToPurge(String schoolId, long purgeLimit);

    @Query("SELECT c.id FROM P2PSyncInfo c INNER JOIN (SELECT a.id, COUNT(*) AS ranknum FROM P2PSyncInfo AS a INNER JOIN P2PSyncInfo AS b ON a.school_id =:schoolId AND b.school_id =:schoolId AND (a.sender = b.sender) AND (a.sequence <= b.sequence) GROUP BY a.id HAVING COUNT(*) <= :limit) AS d ON (c.id = d.id and c.school_id=:schoolId) ORDER BY c.sender, d.ranknum")
    public Long[] findTopMessagesToRetain(String schoolId, long limit);

    @Query("DELETE FROM P2PSyncInfo WHERE school_id=:schoolId AND id not in (:ids)")
    public void purgeMessages(String schoolId, List<Long> ids);
}
