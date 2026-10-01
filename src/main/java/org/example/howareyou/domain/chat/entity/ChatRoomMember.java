package org.example.howareyou.domain.chat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.howareyou.domain.member.entity.Member;
import org.example.howareyou.global.config.BaseTime;
import org.example.howareyou.global.entity.BaseEntity;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_room_member",
        columnNames = {"chat_room_id","member_id"}
))
@Getter
@Setter
@NoArgsConstructor
public class ChatRoomMember extends BaseTime {

  @Id
  @GeneratedValue
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_room_id")
  private ChatRoom chatRoom;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @Enumerated(EnumType.STRING)
  private ChatRoomMemberStatus status;

  /* 그룹 채팅을 위해 아래 추가 */
  @Enumerated(EnumType.STRING)
  private ChatRoomMemberRole role;

  private LocalDateTime joinedAt;   // 실제로 참여를 시작한 시각
  private LocalDateTime leftAt;   // 나간 시각

  public ChatRoomMember(ChatRoom chatRoom, Member member, ChatRoomMemberStatus status) {
    this.chatRoom = chatRoom;
    this.member = member;
    this.status = status;
  }
}
