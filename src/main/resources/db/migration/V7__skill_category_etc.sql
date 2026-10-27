-- 기술 카테고리에 ETC(기타) 추가 (이슈 #33, FE-05·FE-07)
--
-- 웹에서 카테고리를 고르는 칸을 없앴다(사용자 결정 2026-10-27). 사용자는 기술 이름만 넣고,
-- 사전에 없는 기술은 카테고리 없이 들어온다. 그런 기술을 ETC 로 등록한다.
-- 그동안 웹이 카테고리 대신 보내던 TOOL(협업 도구) 기록은 그대로 둔다 — 사용자가 고른 값이 아니라
-- 제약을 피하려고 보낸 값이지만, 사전 보강 때 사람이 보고 고칠 몫이다.
ALTER TABLE app.skill
    DROP CONSTRAINT IF EXISTS skill_category_check;

ALTER TABLE app.skill
    ADD CONSTRAINT skill_category_check
        CHECK (category IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'INFRA', 'TOOL', 'ETC'));
