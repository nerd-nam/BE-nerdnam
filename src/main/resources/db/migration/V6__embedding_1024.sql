-- 임베딩 차원 768 → 1024 (BE-19, 이슈 #31)
--
-- AI 서빙이 AWS Bedrock 으로 바뀌어 임베딩 모델이 Amazon Titan Text Embeddings V2 가 됐다
-- (SPEC ADR-0007, AI 레포 PR #30). 1024 로 확정됐다(사용자 결정 2026-10-27).
-- Titan V2 가 내는 차원은 256·512·1024 뿐이라
-- 768 로 맞출 수 없다. 차원이 다른 벡터는 pgvector 가 거리 계산을 못 한다
-- ("ERROR: different vector dimensions 768 and 1024").
--
-- 저장된 768차원 벡터는 새 모델과 비교할 수 없으므로 버리고 다시 임베딩한다.
-- 청크 텍스트·메타데이터는 그대로 두고 embedding_status 만 PENDING 으로 돌리면,
-- 대기 청크 재처리 작업(ChunkEmbedder, 5분마다)이 새 모델로 다시 채운다.
-- ck_chunk_embedding_ready 가 "READY = 벡터·모델 있음 / PENDING = 벡터 없음"을 강제하므로
-- 세 칸을 한 번에 바꾼다.
UPDATE rag.chunk
SET embedding_status = 'PENDING',
    embedding        = NULL,
    embedding_model  = NULL
WHERE embedding IS NOT NULL;

-- 비운 뒤에 차원을 바꾼다 (값이 남아 있으면 ALTER 가 실패한다)
ALTER TABLE rag.chunk
    ALTER COLUMN embedding TYPE public.vector(1024);

COMMENT ON COLUMN rag.chunk.embedding IS
    'Amazon Titan Text Embeddings V2 = 1024차원 (ADR-0007). 모델 교체 시 마이그레이션 + 전체 재임베딩';
