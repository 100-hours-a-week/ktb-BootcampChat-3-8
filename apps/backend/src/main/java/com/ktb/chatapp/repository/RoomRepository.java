package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    // 페이지네이션과 함께 모든 방 조회
    Page<Room> findAll(Pageable pageable);

    // 검색어와 함께 페이지네이션 조회
    Page<Room> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // 가장 최근에 생성된 방 조회 (Health Check용)
    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    Optional<Room> findMostRecentRoom();

    // Health Check용 단순 조회 (지연 시간 측정)
    @Query(value = "{}", fields = "{ '_id': 1 }")
    Optional<Room> findOneForHealthCheck();

    /**
     * 참가자 추가 (원자적 업데이트 + participantCount 동기화)
     * $addToSet: 중복 방지하며 배열에 추가
     * $inc: participantCount 증가 (추가 성공 시에만)
     */
    @Query("{'_id': ?0, 'participantIds': {$ne: ?1}}")
    @Update("{'$addToSet': {'participantIds': ?1}, '$inc': {'participantCount': 1}}")
    void addParticipant(String roomId, String userId);

    /**
     * 참가자 제거 (원자적 업데이트 + participantCount 동기화)
     * $pull: 배열에서 제거
     * $inc: participantCount 감소 (제거 성공 시에만)
     */
    @Query("{'_id': ?0, 'participantIds': ?1}")
    @Update("{'$pull': {'participantIds': ?1}, '$inc': {'participantCount': -1}}")
    void removeParticipant(String roomId, String userId);
}
