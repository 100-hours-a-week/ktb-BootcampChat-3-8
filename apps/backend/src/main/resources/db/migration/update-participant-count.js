// MongoDB 마이그레이션 스크립트: participantCount 필드 동기화 및 인덱스 생성
// 실행 방법: mongosh <connection-string> < update-participant-count.js

// 사용 중인 데이터베이스 선택
use('bootcamp-chat');

print('=== Step 1: participantCount 필드 동기화 시작 ===');

let updateCount = 0;
let totalCount = db.rooms.countDocuments();

// 모든 Room 문서 업데이트
db.rooms.find().forEach(function(room) {
    const participantCount = room.participantIds ? room.participantIds.length : 0;

    db.rooms.updateOne(
        { _id: room._id },
        {
            $set: { participantCount: participantCount }
        }
    );

    updateCount++;
    if (updateCount % 100 === 0) {
        print(`Progress: ${updateCount}/${totalCount} rooms updated...`);
    }
});

print(`=== Step 1 완료: ${updateCount}개 방 업데이트 ===\n`);

// Step 2: 복합 인덱스 생성
print('=== Step 2: 복합 인덱스 생성 시작 ===');

// 기존 인덱스 확인
print('기존 인덱스:');
db.rooms.getIndexes().forEach(function(index) {
    print(`  - ${index.name}`);
});

// 참가자 수 기준 정렬 인덱스 (인기순)
try {
    db.rooms.createIndex(
        { participantCount: -1, createdAt: -1 },
        { name: 'participantCount_createdAt_idx', background: true }
    );
    print('✓ participantCount_createdAt_idx 생성 완료');
} catch (e) {
    print(`✗ participantCount_createdAt_idx 생성 실패: ${e.message}`);
}

// 생성일 기준 정렬 인덱스 (최신순)
try {
    db.rooms.createIndex(
        { createdAt: -1, participantCount: -1 },
        { name: 'createdAt_participantCount_idx', background: true }
    );
    print('✓ createdAt_participantCount_idx 생성 완료');
} catch (e) {
    print(`✗ createdAt_participantCount_idx 생성 실패: ${e.message}`);
}

// 단일 participantCount 인덱스 (이미 Room.java에 @Indexed로 정의됨)
try {
    db.rooms.createIndex(
        { participantCount: 1 },
        { name: 'participantCount', background: true }
    );
    print('✓ participantCount 단일 인덱스 생성 완료');
} catch (e) {
    print(`✗ participantCount 단일 인덱스 생성 실패: ${e.message}`);
}

print('=== Step 2 완료 ===\n');

print('=== Migration completed! ===\n');

// 결과 확인
const mismatchCount = db.rooms.aggregate([
    {
        $project: {
            _id: 1,
            name: 1,
            participantCount: 1,
            actualCount: { $size: { $ifNull: ["$participantIds", []] } },
            mismatch: {
                $ne: [
                    "$participantCount",
                    { $size: { $ifNull: ["$participantIds", []] } }
                ]
            }
        }
    },
    {
        $match: { mismatch: true }
    },
    {
        $count: "mismatchCount"
    }
]).toArray();

if (mismatchCount.length > 0) {
    print(`WARNING: ${mismatchCount[0].mismatchCount} rooms still have mismatched participantCount!`);
} else {
    print('All rooms have correct participantCount.');
}
