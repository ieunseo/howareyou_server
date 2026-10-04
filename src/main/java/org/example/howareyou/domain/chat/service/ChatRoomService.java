package org.example.howareyou.domain.chat.service;

import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.example.howareyou.domain.chat.dto.*;
import org.example.howareyou.domain.chat.entity.*;
import org.example.howareyou.domain.chat.repository.ChatRoomMemberRepository;
import org.example.howareyou.domain.chat.repository.ChatRoomRepository;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageDocument;
import org.example.howareyou.domain.chat.websocket.service.ChatRedisService;
import org.example.howareyou.domain.member.entity.Member;
import org.example.howareyou.domain.chat.websocket.repository.ChatMessageDocumentRepository;
import org.example.howareyou.domain.member.repository.MemberRepository;
import org.example.howareyou.domain.member.service.MemberService;
import org.example.howareyou.global.exception.CustomException;
import org.example.howareyou.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomMemberRepository chatRoomMemberRepository;
  private final ChatMessageDocumentRepository chatMessageDocumentRepository;
  private final MemberRepository memberRepository;
  private final ChatRedisService chatRedisService;
  private final MemberService memberService;
  /**
   *  채팅방 생성
   */
  @Transactional
  public CreateChatRoomResponse createChatRoom(CreateChatRoomRequest request, Long senderId) {
    Long receiverId = memberService.getIdByMembername(request.getMembername());

    Member sender = memberRepository.findById(senderId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    Member receiver = memberRepository.findById(receiverId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 이미 둘 사이에 존재하는 채팅방 확인
    ChatRoom existingRoom = chatRoomRepository.findByMembers(senderId, receiverId);
    if (existingRoom != null) {
      return new CreateChatRoomResponse(existingRoom.getUuid());
    }

    // 새로운 채팅방 생성
    ChatRoom chatRoom = new ChatRoom();
    chatRoom.setStatus(ChatRoomStatus.PENDING);
    chatRoomRepository.save(chatRoom);

    // 참여자 추가 (초기 상태는 PENDING)
    ChatRoomMember senderEntry = new ChatRoomMember(chatRoom, sender, ChatRoomMemberStatus.SENDER);
    chatRoom.addMember(senderEntry);
    ChatRoomMember receiverEntry = new ChatRoomMember(chatRoom, receiver, ChatRoomMemberStatus.RECEIVER);
    chatRoom.addMember(receiverEntry);

    chatRoomMemberRepository.save(senderEntry);
    chatRoomMemberRepository.save(receiverEntry);

    return new CreateChatRoomResponse(chatRoom.getUuid());
  }

  /**
   *  그룹 채팅방 생성
   */
  @Transactional
  public CreateChatRoomResponse createGroupChatRoom(CreateGroupChatRoomRequest request, Long ownerId) {
    // 1. 방장 조회
    Member owner = memberRepository.findById(ownerId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 2~4. 이름 정리(중복·나 자신 제거) → 빈 목록 검사 → Member 변환
    List<Member> invitees = resolveInvitees(request.getInviteeMembernames(), owner.getMembername());

    // 5. 방 생성
    ChatRoom chatRoom = new ChatRoom();
    chatRoom.setType(ChatRoomType.GROUP);
    chatRoom.setStatus(ChatRoomStatus.ACTIVE);
    chatRoom.setTitle(request.getTitle());
    chatRoomRepository.save(chatRoom);

    // 6. 방장 등록
    ChatRoomMember ownerEntry = ChatRoomMember.owner(chatRoom, owner);
    chatRoom.addMember(ownerEntry);
    chatRoomMemberRepository.save(ownerEntry);

    // 7. 초대자 등록
    for (Member invitee : invitees) {
      ChatRoomMember inviteeEntry = ChatRoomMember.invitee(chatRoom, invitee);
      chatRoom.addMember(inviteeEntry);
      chatRoomMemberRepository.save(inviteeEntry);
    }

    return new CreateChatRoomResponse(chatRoom.getUuid());
  }

  /**
   *  그룹 채팅방에 추가 초대 (JOINED 참여자만 가능)
   *  - 처음 초대: 새 행 INSERT
   *  - LEFT/REJECTED: 유니크 제약 때문에 기존 행을 INVITED로 되돌림
   *  - 이미 JOINED/INVITED: 조용히 건너뜀
   */
  @Transactional
  public void inviteToGroupChatRoom(String roomUuid, InviteGroupMembersRequest request, Long inviterId) {
    ChatRoom room = getActiveGroupRoom(roomUuid);
    ChatRoomMember inviterEntry = getMyEntry(roomUuid, inviterId);
    if (inviterEntry.getStatus() != ChatRoomMemberStatus.JOINED) {
      throw new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }

    List<Member> invitees = resolveInvitees(
        request.getInviteeMembernames(), inviterEntry.getMember().getMembername());

    for (Member invitee : invitees) {
      chatRoomMemberRepository.findByChatRoom_UuidAndMember_Id(roomUuid, invitee.getId())
          .ifPresentOrElse(
              existing -> {
                if (existing.getStatus() == ChatRoomMemberStatus.LEFT
                    || existing.getStatus() == ChatRoomMemberStatus.REJECTED) {
                  existing.reinvite();
                }
              },
              () -> {
                ChatRoomMember entry = ChatRoomMember.invitee(room, invitee);
                room.addMember(entry);
                chatRoomMemberRepository.save(entry);
              });
    }
  }

  /**
   *  그룹 초대 수락 (본인 행만 JOINED + joinedAt)
   */
  @Transactional
  public void acceptGroupInvitation(String roomUuid, Long memberId) {
    getActiveGroupRoom(roomUuid); // CLOSED 방의 초대는 수락 불가
    ChatRoomMember me = getMyEntry(roomUuid, memberId);

    if (me.getStatus() == ChatRoomMemberStatus.JOINED) return; // 이미 참여 중
    if (me.getStatus() != ChatRoomMemberStatus.INVITED) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }
    me.acceptInvitation();
  }

  /**
   *  그룹 초대 거절 (본인 행만 REJECTED, 방 상태는 그대로)
   */
  @Transactional
  public void rejectGroupInvitation(String roomUuid, Long memberId) {
    ChatRoom room = chatRoomRepository.findByUuid(roomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    if (!room.isGroup()) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }
    ChatRoomMember me = getMyEntry(roomUuid, memberId);

    if (me.getStatus() == ChatRoomMemberStatus.REJECTED) return; // 이미 거절
    if (me.getStatus() != ChatRoomMemberStatus.INVITED) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }
    me.rejectInvitation();
  }

  /**
   *  그룹 채팅방 나가기
   *  - 본인 LEFT + leftAt
   *  - 방장이 나가면 joinedAt 가장 빠른 JOINED 참여자에게 OWNER 위임
   *  - JOINED 0명이 되면 방 CLOSED (메시지는 단어장 참조 때문에 삭제하지 않음)
   */
  @Transactional
  public void leaveGroupChatRoom(String roomUuid, Long memberId) {
    ChatRoom room = chatRoomRepository.findByUuid(roomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    if (!room.isGroup()) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }
    ChatRoomMember me = getMyEntry(roomUuid, memberId);
    if (me.getStatus() != ChatRoomMemberStatus.JOINED) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }

    boolean wasOwner = me.isOwner();
    me.leave();

    List<ChatRoomMember> remaining = chatRoomMemberRepository.findByChatRoom(room).stream()
        .filter(e -> e.getStatus() == ChatRoomMemberStatus.JOINED)
        .toList();

    if (remaining.isEmpty()) {
      room.setStatus(ChatRoomStatus.CLOSED);
    } else if (wasOwner) {
      remaining.stream()
          .min(Comparator.comparing(ChatRoomMember::getJoinedAt,
              Comparator.nullsLast(Comparator.naturalOrder())))
          .ifPresent(next -> next.setRole(ChatRoomMemberRole.OWNER));
    }

    // 안 읽은 수·접속 정보 정리
    chatRedisService.resetUnread(roomUuid, memberId.toString());
  }

  /**
   *  내가 받은 그룹 초대 목록 (INVITED + 방 ACTIVE)
   */
  @Transactional
  public List<GroupInvitationResponse> getMyGroupInvitations(Long myId) {
    return chatRoomMemberRepository
        .findByMemberIdAndStatusAndRoomStatusOrderByRoomCreatedDesc(
            myId, ChatRoomMemberStatus.INVITED, ChatRoomStatus.ACTIVE)
        .stream()
        .map(entry -> {
          ChatRoom room = entry.getChatRoom();
          String ownerName = room.getMembers().stream()
              .filter(ChatRoomMember::isOwner)
              .map(m -> m.getMember().getMembername())
              .findFirst()
              .orElse(null);
          return new GroupInvitationResponse(
              room.getUuid(),
              room.getTitle(),
              ownerName,
              room.countJoinedMembers(),
              entry.getCreatedAt()
          );
        })
        .toList();
  }

  // 초대 이름 정리: 중복·나 자신 제거 → 비면 400 → 없는 이름은 404
  private List<Member> resolveInvitees(List<String> rawNames, String myName) {
    List<String> names = rawNames.stream()
        .distinct()
        .filter(name -> !Objects.equals(myName, name)) // 내 membername이 아직 없는(null) 계정도 안전
        .toList();

    if (names.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "초대할 사람이 없습니다");
    }

    return names.stream()
        .map(name -> memberRepository.findByMembername(name)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND, "존재하지 않는 사용자: " + name)))
        .toList();
  }

  // ACTIVE 상태의 그룹방만 통과
  private ChatRoom getActiveGroupRoom(String roomUuid) {
    ChatRoom room = chatRoomRepository.findByUuid(roomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    if (!room.isGroup() || room.getStatus() != ChatRoomStatus.ACTIVE) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }
    return room;
  }

  // 이 방에서 나의 참여 행 (없으면 403)
  private ChatRoomMember getMyEntry(String roomUuid, Long memberId) {
    return chatRoomMemberRepository.findByChatRoom_UuidAndMember_Id(roomUuid, memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
  }

  /**
   *  채팅 요청 수락
   */
  @Transactional
  public void acceptChatRoom(String roomUuid, Long receiverId) {
    ChatRoom room = chatRoomRepository.findByUuid(roomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

    // 이미 처리된 방
    if (room.getStatus() == ChatRoomStatus.ACCEPTED) return;
    if (room.getStatus() == ChatRoomStatus.REJECTED)
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);

    // 수락자 검증 (방 참가자 + RECEIVER 여야 함)
    memberRepository.findById(receiverId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    List<ChatRoomMember> entries = chatRoomMemberRepository.findByChatRoom(room);
    ChatRoomMember me = entries.stream()
        .filter(e -> e.getMember().getId().equals(receiverId))
        .findFirst()
        .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

    if (me.getStatus() != ChatRoomMemberStatus.RECEIVER)
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);

    // 두 명 모두 JOINED로 전환
    LocalDateTime now = LocalDateTime.now();
    for (ChatRoomMember e : entries) {
      if (e.getStatus() == ChatRoomMemberStatus.SENDER || e.getStatus() == ChatRoomMemberStatus.RECEIVER) {
        e.setStatus(ChatRoomMemberStatus.JOINED);
        e.setJoinedAt(now);
      }
    }

    room.setStatus(ChatRoomStatus.ACCEPTED);
  }


  @Transactional
  public void rejectChatRoom(String roomUuid, Long receiverId) {
    ChatRoom room = chatRoomRepository.findByUuid(roomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

    // 상태 검증
    if (room.getStatus() == ChatRoomStatus.ACCEPTED) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE); // 이미 수락된 방 거절 불가
    }
    if (room.getStatus() == ChatRoomStatus.REJECTED) {
      return;
    }

    // 참가자 및 역할 검증
    memberRepository.findById(receiverId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (!room.hasParticipant(receiverId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }

    List<ChatRoomMember> entries = chatRoomMemberRepository.findByChatRoom(room);
    ChatRoomMember myEntry = entries.stream()
        .filter(e -> e.getMember().getId().equals(receiverId))
        .findFirst()
        .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

    // RECEIVER만 거절 허용
    if (myEntry.getStatus() != ChatRoomMemberStatus.RECEIVER) {
      throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
    }

    // 상태 전환
    for (ChatRoomMember e : entries) {
      switch (e.getStatus()) {
        case SENDER, RECEIVER -> {
          e.setStatus(ChatRoomMemberStatus.REJECTED);
        }
        case JOINED -> throw new CustomException(ErrorCode.INVALID_CHAT_ROOM_STATE);
        case REJECTED -> {} // no-op
      }
    }
    room.setStatus(ChatRoomStatus.REJECTED);

    chatRoomMemberRepository.deleteAll(entries);
    chatRoomRepository.delete(room);

  }

  /**
   *  uuid 채팅방 단 건 조회
   */
  @Transactional
  public ChatRoomResponse getChatRoom(String uuid, Long myId) {
    //채팅방 uuid
    ChatRoom chatRoom = chatRoomRepository.findByUuid(uuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

    // 그 방에서 나의 참여 정보
    ChatRoomMember memberEntry = chatRoomMemberRepository
        .findByChatRoomAndMemberId(chatRoom, myId)
        .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

    // join 이 아니면에러
    if (memberEntry.getStatus() != ChatRoomMemberStatus.JOINED) {
      throw new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }
    // 그룹방: 상대방 대신 제목 + JOINED 참여자 목록
    if (chatRoom.isGroup()) {
      ChatRoomResponse response = new ChatRoomResponse(
          chatRoom.getUuid(), chatRoom.getStatus().name(), null, null);
      response.setRoomType(ChatRoomType.GROUP.name());
      response.setTitle(chatRoom.getTitle());
      response.setMembers(chatRoom.getMembers().stream()
          .filter(m -> m.getStatus() == ChatRoomMemberStatus.JOINED)
          .map(m -> new ChatRoomMemberResponse(
              m.getMember().getId(),
              m.getMember().getMembername(),
              m.getRole() != null ? m.getRole().name() : null,
              m.getJoinedAt()))
          .toList());
      return response;
    }

// 나를 제외한 다른 참여자를 꺼냄.
    Member opponent = chatRoom.getOtherParticipant(myId);

    ChatRoomResponse response = new ChatRoomResponse(
        chatRoom.getUuid(),
        chatRoom.getStatus().name(),
        opponent.getId(),
        opponent.getMembername()
    );
    response.setRoomType(ChatRoomType.ONE_TO_ONE.name());
    return response;
  }

  /**
   *  내가 참여 중인 채팅방 목록 조회
   */
  @Transactional
  public List<ChatRoomSummaryResponse> getMyChatRooms(Long myId) {
    Member me = memberRepository.findById(myId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    List<ChatRoomMember> myEntries = chatRoomMemberRepository.findByMember(me);

    return myEntries.stream()
        // 그룹방은 실제 참여 중(JOINED)인 방만 목록에 노출 (초대·거절·나감은 제외)
        .filter(entry -> !entry.getChatRoom().isGroup()
            || entry.getStatus() == ChatRoomMemberStatus.JOINED)
        .map(entry -> {
          ChatRoom room = entry.getChatRoom();
          boolean group = room.isGroup();
          Member opponent = group ? null : room.getOtherParticipant(myId);

          // 마지막 메시지 가져오기 (MongoDB)
          ChatMessageDocument lastMessage = chatMessageDocumentRepository
              .findTopByChatRoomUuidOrderByMessageTimeDesc(room.getUuid())
              .orElse(null);

          // 읽지 않은 메시지 수 조회 (Redis)
          int unreadCount = chatRedisService.getUnreadCount(room.getUuid(), myId.toString());

          return new ChatRoomSummaryResponse(
              room.getUuid(),
              opponent != null ? opponent.getId() : null,
              opponent != null ? opponent.getMembername() : null,
              room.getStatus().name(),
              lastMessage != null ? lastMessage.getContent() : null,
              lastMessage != null ? lastMessage.getMessageTime() : null,
              unreadCount,
              group ? ChatRoomType.GROUP.name() : ChatRoomType.ONE_TO_ONE.name(),
              room.getTitle(),
              room.countJoinedMembers()
          );
        })
        .toList();
  }

  /**
   * ChatRoom 삭제 (disconnection)
    */
  @Transactional
  public void disconnectFromChatRoom(Long memberId, String chatRoomUuid) {
    Member me = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    ChatRoom room = chatRoomRepository.findByUuid(chatRoomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

    // 그룹방은 행을 지우지 않고 LEFT 처리 (재초대·단어장 참조용으로 기록 유지)
    if (room.isGroup()) {
      leaveGroupChatRoom(chatRoomUuid, memberId);
      return;
    }

    // 채팅방 인원 연결 끊기
    chatRoomMemberRepository.deleteByChatRoomAndMember(room, me);

    List<ChatRoomMember> remaining = chatRoomMemberRepository.findByChatRoom(room);

    // 채팅방 id 삭제
    if (remaining.isEmpty()) {
      chatRoomRepository.delete(room);
    }
  }

  /**
   * 단어장 생성을 위한 chatroom 조회
   */
  @Transactional
  public Set<String> getMyChatRoomUuids(Long myId) {
    Member me = memberRepository.findById(myId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    List<ChatRoomMember> myEntries = chatRoomMemberRepository.findByMember(me);

    return myEntries.stream()
//            .filter(entry -> entry.getStatus() == ChatRoomMemberStatus.JOINED)
            // 그룹방: 실제로 대화에 참여했던 방(JOINED, LEFT)만 단어장 대상. 초대만 받았거나 거절한 방은 제외
            .filter(entry -> entry.getStatus() != ChatRoomMemberStatus.INVITED
                    && entry.getStatus() != ChatRoomMemberStatus.REJECTED)
            .map(entry -> entry.getChatRoom().getUuid())
            .collect(Collectors.toSet());
  }

  /**
   * 매칭 수락 되지 않은 대기 방 조회
   */
  private List<ChatRequestSummaryResponse> getRequestsByStatus(Long myId, ChatRoomMemberStatus status) {
    return chatRoomMemberRepository
        .findByMemberIdAndStatusAndRoomStatusOrderByRoomCreatedDesc(
            myId,
            status,
            ChatRoomStatus.PENDING // 요청 상태인 방만
        )
        .stream()
        .map(cm -> {
          ChatRoom room = cm.getChatRoom();
          Member opponent = room.getOtherParticipant(myId);
          return new ChatRequestSummaryResponse(
              room.getUuid(),
              opponent.getId(),
              opponent.getMembername(),
              room.getStatus().name(),
              room.getCreatedAt()
          );
        })
        .toList();
  }

  /** 내가 보낸 요청 리스트 (SENDER + PENDING) */
  public List<ChatRequestSummaryResponse> getSentRequests(Long myId) {
    return getRequestsByStatus(myId, ChatRoomMemberStatus.SENDER);
  }

  /** 내가 받은 요청 리스트 (RECEIVER + PENDING) */
  public List<ChatRequestSummaryResponse> getReceivedRequests(Long myId) {
    return getRequestsByStatus(myId, ChatRoomMemberStatus.RECEIVER);
  }
}