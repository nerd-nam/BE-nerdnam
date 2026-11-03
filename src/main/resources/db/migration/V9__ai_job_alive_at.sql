-- AI 작업의 "살아 있음" 시각 (BE #66, ADR-0009)
--
-- 오래 멈춘 작업 정리는 지금까지 접수 시각부터 쟀다. 그런데 정리는 Pod 마다 돌고, 각 Pod 는 자기가 들고 있는
-- 작업만 안다 — 다른 Pod 가 아직 기다리는 작업을 실패로 지울 수 있다(배포 중에는 Pod 가 2대). AI 호출을
-- 큐로 보내면 큐 대기가 길어져 이 일이 실제로 생긴다. 그래서 작업을 든 Pod 가 1분마다 이 시각을 갱신하고,
-- 정리는 "마지막으로 살아 있던 시각"(없으면 접수 시각)부터 잰다.
ALTER TABLE app.ai_job ADD COLUMN alive_at timestamptz;

COMMENT ON COLUMN app.ai_job.alive_at IS '작업을 든 Pod 가 마지막으로 살아 있다고 남긴 시각. 없으면 접수 시각으로 본다';
