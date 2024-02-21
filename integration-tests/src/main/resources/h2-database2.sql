CREATE TABLE public.user_role (
    id varchar(64) NOT NULL,
    user_id varchar(64) NULL,
    role_id varchar(64) NULL,
    delete_flag bool NOT NULL DEFAULT false,
    CONSTRAINT user_role_pkey PRIMARY KEY (id)
);


INSERT INTO public.user_role
(id, user_id, role_id, delete_flag)
VALUES('defc2d01-fb38-4d31-b006-fd182b25aa33', '9ffec3c4-2342-427c-a0ec-e22e5f2ec732', '2c6a06d8-8e10-49c4-88fe-7d2f05dd073b', false);
