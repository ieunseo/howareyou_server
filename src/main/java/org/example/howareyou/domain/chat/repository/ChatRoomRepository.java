package org.example.howareyou.domain.chat.repository;

import java.util.Optional;
import org.example.howareyou.domain.chat.entity.ChatRoom;
import org.example.howareyou.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

  Optional<ChatRoom> findByUuid(String uuid);

  @Query("""
    SELECT DISTINCT c
    FROM ChatRoom c
    JOIN c.members m1
    JOIN c.members m2
    WHERE m1.member.id = :member1Id
      AND m2.member.id = :member2Id
      AND (c.type IS NULL OR c.type <> org.example.howareyou.domain.chat.entity.ChatRoomType.GROUP)
  """)
  ChatRoom findByMembers(@Param("member1Id") Long member1Id, @Param("member2Id") Long member2Id);

  @Query("select m.member from ChatRoomMember m " +
      "where m.chatRoom.uuid = :uuid and m.member.id <> :senderId")
  Member findReceiverByRoomUuidAndSenderId(@Param("uuid") String uuid,
      @Param("senderId") Long senderId);
}
