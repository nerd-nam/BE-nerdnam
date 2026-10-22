-- =====================================================================
-- V2: 표준 기술 사전 초기 데이터 (예시)
--   name_key 규칙: 소문자로 바꾸고 공백을 모두 지운다. (SkillNameKey.of 와 같은 규칙)
--   사전은 계속 보강한다. 운영 중 추가는 V3__, V4__ ... 새 마이그레이션으로 넣는다.
-- =====================================================================

INSERT INTO app.skill (name, name_key, category) VALUES
    ('Java',        'java',        'LANGUAGE'),
    ('Kotlin',      'kotlin',      'LANGUAGE'),
    ('Python',      'python',      'LANGUAGE'),
    ('TypeScript',  'typescript',  'LANGUAGE'),
    ('JavaScript',  'javascript',  'LANGUAGE'),
    ('Go',          'go',          'LANGUAGE'),
    ('Spring Boot', 'springboot',  'FRAMEWORK'),
    ('Spring',      'spring',      'FRAMEWORK'),
    ('JPA',         'jpa',         'FRAMEWORK'),
    ('React',       'react',       'FRAMEWORK'),
    ('Next.js',     'next.js',     'FRAMEWORK'),
    ('FastAPI',     'fastapi',     'FRAMEWORK'),
    ('PostgreSQL',  'postgresql',  'DATABASE'),
    ('MySQL',       'mysql',       'DATABASE'),
    ('Redis',       'redis',       'DATABASE'),
    ('MongoDB',     'mongodb',     'DATABASE'),
    ('Kafka',       'kafka',       'INFRA'),
    ('Docker',      'docker',      'INFRA'),
    ('Kubernetes',  'kubernetes',  'INFRA'),
    ('AWS',         'aws',         'INFRA'),
    ('Git',         'git',         'TOOL'),
    ('Jira',        'jira',        'TOOL');

INSERT INTO app.skill_alias (skill_id, alias, alias_key)
SELECT s.id, a.alias, a.alias_key
FROM (VALUES
    ('java',       '자바',          '자바'),
    ('springboot', '스프링부트',    '스프링부트'),
    ('springboot', 'spring-boot',  'spring-boot'),
    ('spring',     'Spring Framework', 'springframework'),
    ('spring',     '스프링',        '스프링'),
    ('jpa',        'Spring Data JPA', 'springdatajpa'),
    ('react',      'react.js',     'react.js'),
    ('react',      'reactjs',      'reactjs'),
    ('react',      '리액트',        '리액트'),
    ('next.js',    'nextjs',       'nextjs'),
    ('postgresql', 'postgres',     'postgres'),
    ('postgresql', 'PG',           'pg'),
    ('kafka',      'Apache Kafka', 'apachekafka'),
    ('kubernetes', 'k8s',          'k8s'),
    ('kubernetes', '쿠버네티스',     '쿠버네티스'),
    ('typescript', 'TS',           'ts'),
    ('javascript', 'JS',           'js')
) AS a(skill_key, alias, alias_key)
JOIN app.skill s ON s.name_key = a.skill_key;
