-- 삭제한 프로젝트의 이전 분석에는 이름만 남긴다. 청크를 지워도 작성 방향의 인용 이름은 유지한다.
ALTER TABLE app.match_evidence ADD COLUMN source_deleted boolean NOT NULL DEFAULT false;
ALTER TABLE app.writing_guide ADD COLUMN citation_snapshots jsonb;
