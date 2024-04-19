drop table public.user_role if exists;

create table public.user_role (
    id varchar(64) not null,
    user_id varchar(64) null,
    role_id varchar(64) null,
    delete_flag bool not null default false,
    auth_user varchar(64) null,
    constraint user_role_pkey primary key (id)
);


insert into public.user_role
(id, user_id, role_id, delete_flag, auth_user)
values('defc2d01-fb38-4d31-b006-fd182b25aa33', '9ffec3c4-2342-427c-a0ec-e22e5f2ec732', '2c6a06d8-8e10-49c4-88fe-7d2f05dd073b', false, '{"id":"123"}');
