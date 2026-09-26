-- Marketlist: armazenamento único por usuário (PWA + Android)
create table if not exists public.marketlist_data (
  user_id uuid primary key references auth.users(id) on delete cascade,
  items text not null default '[]',
  budget numeric(12,2) not null default 0,
  history text not null default '[]',
  updated_at timestamptz not null default now()
);

alter table public.marketlist_data enable row level security;

drop policy if exists "marketlist_data_select_own" on public.marketlist_data;
drop policy if exists "marketlist_data_insert_own" on public.marketlist_data;
drop policy if exists "marketlist_data_update_own" on public.marketlist_data;
drop policy if exists "marketlist_data_delete_own" on public.marketlist_data;

create policy "marketlist_data_select_own"
on public.marketlist_data for select
to authenticated
using (auth.uid() = user_id);

create policy "marketlist_data_insert_own"
on public.marketlist_data for insert
to authenticated
with check (auth.uid() = user_id);

create policy "marketlist_data_update_own"
on public.marketlist_data for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "marketlist_data_delete_own"
on public.marketlist_data for delete
to authenticated
using (auth.uid() = user_id);

grant select, insert, update, delete on public.marketlist_data to authenticated;
