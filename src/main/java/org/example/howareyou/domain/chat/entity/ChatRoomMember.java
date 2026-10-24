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

  /* ───────── 그룹 채팅용 정적 팩토리 ───────── */

  // 방장: 만들자마자 참여 중
  public static ChatRoomMember owner(ChatRoom chatRoom, Member member) {
    ChatRoomMember m = new ChatRoomMember(chatRoom, member, ChatRoomMemberStatus.JOINED);
    m.role = ChatRoomMemberRole.OWNER;
    m.joinedAt = LocalDateTime.now();
    return m;
  }

  // 초대받은 사람: 수락 전이라 joinedAt 없음
  public static ChatRoomMember invitee(ChatRoom chatRoom, Member member) {
    ChatRoomMember m = new ChatRoomMember(chatRoom, member, ChatRoomMemberStatus.INVITED);
    m.role = ChatRoomMemberRole.MEMBER;
    return m;
  }

  /* ───────── 그룹 채팅 상태 변경 ───────── */

  // 초대 수락
  public void acceptInvitation() {
    this.status = ChatRoomMemberStatus.JOINED;
    this.joinedAt = LocalDateTime.now();
  }

  // 초대 거절 (본인 행만 변경)
  public void rejectInvitation() {
    this.status = ChatRoomMemberStatus.REJECTED;
  }

  // 나가기
  public void leave() {
    this.status = ChatRoomMemberStatus.LEFT;
    this.role = ChatRoomMemberRole.MEMBER;
    this.leftAt = LocalDateTime.now();
  }

  // 나갔거나 거절한 사람 재초대 (유니크 제약 때문에 새 행 대신 기존 행 재사용)
  public void reinvite() {
    this.status = ChatRoomMemberStatus.INVITED;
    this.role = ChatRoomMemberRole.MEMBER;
    this.joinedAt = null;
    this.leftAt = null;
  }

  public boolean isOwner() {
    return this.role == ChatRoomMemberRole.OWNER;
  }
}
