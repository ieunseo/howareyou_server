package org.example.howareyou.domain.chat.service;

import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.example.howareyou.domain.chat.dto.ChatRequestSummaryResponse;
import org.example.howareyou.domain.chat.dto.ChatRoomResponse;
import org.example.howareyou.domain.chat.dto.ChatRoomSummaryResponse;
import org.example.howareyou.domain.chat.dto.CreateChatRoomRequest;
import org.example.howareyou.domain.chat.dto.CreateChatRoomResponse;
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
    Instant now = Instant.now();
    for (ChatRoomMember e : entries) {
      if (e.getStatus() == ChatRoomMemberStatus.SENDER || e.getStatus() == ChatRoomMemberStatus.RECEIVER) {
        e.setStatus(ChatRoomMemberStatus.JOINED);
//        e.setJoinedAt(now);
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
// 나를 제외한 다른 참여자를 꺼냄.
    Member opponent = chatRoom.getOtherParticipant(myId);

    return new ChatRoomResponse(
        chatRoom.getUuid(),
        chatRoom.getStatus().name(),
        opponent.getId(),
        opponent.getMembername()
    );
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
        .map(entry -> {
          ChatRoom room = entry.getChatRoom();
          Member opponent = room.getOtherParticipant(myId);

          // 마지막 메시지 가져오기 (MongoDB)
          ChatMessageDocument lastMessage = chatMessageDocumentRepository
              .findTopByChatRoomUuidOrderByMessageTimeDesc(room.getUuid())
              .orElse(null);

          // 읽지 않은 메시지 수 조회 (Redis)
          int unreadCount = chatRedisService.getUnreadCount(room.getUuid(), myId.toString());

          return new ChatRoomSummaryResponse(
              room.getUuid(),
              opponent.getId(),
              opponent.getMembername(),
              room.getStatus().name(),
              lastMessage != null ? lastMessage.getContent() : null,
              lastMessage != null ? lastMessage.getMessageTime() : null,
              unreadCount
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