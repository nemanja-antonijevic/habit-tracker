# Habit Tracker — API Reference

Kompletna specifikacija svih HTTP endpointa. Ovo je izvor istine za API ugovor — dopunjava se uz svaku izmenu endpointa.

- **Base URL (lokalno):** `http://localhost:8080`
- **Content-Type:** `application/json` (zahtevi sa telom)
- **Setup i pokretanje:** vidi [README.md](../README.md)
- **Brzi copy-paste curl primeri:** vidi [curls.md](../curls.md)

Sve rute su pod `/habits` (`HabitController`).

## Pregled endpointa

| # | Metoda | Putanja | Namena | CQRS strana |
|---|--------|---------|--------|-------------|
| 1 | `POST` | `/habits` | Kreiraj naviku | Write |
| 2 | `GET` | `/habits` | Lista navika (paginirano) | Write |
| 3 | `GET` | `/habits/{id}` | Jedna navika po id | Write |
| 4 | `PUT` | `/habits/{id}` | Izmeni naziv (optimistic lock) | Write |
| 5 | `DELETE` | `/habits/{id}` | Obriši naviku | Write |
| 6 | `POST` | `/habits/{id}/complete` | Označi kao odrađeno danas | Write + event |
| 7 | `POST` | `/habits/{id}/uncomplete` | Poništi današnje označavanje | Write + event |
| 8 | `POST` | `/habits/{id}/archive` | Arhiviraj (soft) | Write |
| 9 | `POST` | `/habits/{id}/unarchive` | Vrati iz arhive | Write |
| 10 | `GET` | `/habits/{id}/history` | Dani kad je navika odrađena | Write (direktno čitanje) |
| 11 | `GET` | `/habits/{id}/stats` | Agregatna statistika | Read-model (Kafka projekcija) |

## Model podataka

### HabitResponse

Standardni prikaz navike. Vraćaju ga endpointi 1, 3, 4, 6, 7, 8, 9.

| Polje | Tip | Opis |
|-------|-----|------|
| `id` | `long` | Identifikator navike |
| `name` | `string` | Naziv |
| `completionCount` | `int` | Ukupan broj odrađenih dana |
| `currentStreak` | `int` | Trenutni niz uzastopnih dana |
| `createdAt` | `string` (ISO-8601 instant) | Vreme kreiranja |

Namerno se NE izlažu (interna polja): `version` (osim kao ulaz na update), `archived`, `longestStreak`, `lastCompletedAt`.

### HabitCompletionResponse

Jedan odrađen dan. Vraća ga endpoint 10 kao niz.

| Polje | Tip | Opis |
|-------|-----|------|
| `id` | `long` | Identifikator reda u istoriji |
| `completedOn` | `string` (ISO-8601 date) | Datum (`YYYY-MM-DD`) |

### HabitStatsResponse

Agregatna statistika. Vraća je endpoint 11.

| Polje | Tip | Opis |
|-------|-----|------|
| `completionCount` | `long` | Ukupan broj odrađenih dana |
| `longestStreak` | `int` | Najduži niz ikad zabeležen |
| `lastCompletedOn` | `string` (ISO-8601 date) \| `null` | Poslednji odrađen datum |
| `currentStreak` | `int` | Trenutni niz, korigovan na vreme čitanja (vidi endpoint 11) |

### ErrorResponse

Telo svih grešaka.

| Polje | Tip | Opis |
|-------|-----|------|
| `error` | `string` | Poruka greške |

## Status kodovi

| Kod | Kada |
|-----|------|
| `200 OK` | Uspeh sa telom |
| `201 Created` | Navika kreirana (uz `Location` header) |
| `204 No Content` | Uspeh bez tela (brisanje) |
| `400 Bad Request` | Validacija pala, pokvaren JSON, ili nedozvoljen prelaz stanja (`InvalidHabitStateException`) |
| `404 Not Found` | Navika ne postoji (`HabitNotFoundException`) |
| `409 Conflict` | Neslaganje verzije pri izmeni (`HabitVersionConflictException`) |

Mapiranje izuzetak → status je centralizovano u `GlobalExceptionHandler` (`@RestControllerAdvice`).

---

## 1. Kreiraj naviku

```
POST /habits
```

Telo zahteva:

| Polje | Tip | Ograničenja |
|-------|-----|-------------|
| `name` | `string` | `@NotBlank`, `@Size(max = 255)` |

```json
{ "name": "Read 30 min" }
```

**Odgovor:** `201 Created`, header `Location: /habits/{id}`, telo `HabitResponse`.

```json
{ "id": 42, "name": "Read 30 min", "completionCount": 0, "currentStreak": 0, "createdAt": "2026-06-26T08:30:00Z" }
```

| Status | Uslov |
|--------|-------|
| `201` | Kreirano |
| `400` | `name` prazan ili duži od 255 / pokvareno telo |

## 2. Lista navika

```
GET /habits
```

Query parametri (Spring `Pageable`):

| Parametar | Tip | Default | Opis |
|-----------|-----|---------|------|
| `page` | `int` | `0` | Indeks strane (od 0) |
| `size` | `int` | `20` | Veličina strane |
| `sort` | `string` | — | `polje,asc\|desc` (npr. `createdAt,desc`) |

**Odgovor:** `200 OK`, Spring `Page<HabitResponse>`.

```json
{
  "content": [ { "id": 42, "name": "Read 30 min", "completionCount": 0, "currentStreak": 0, "createdAt": "2026-06-26T08:30:00Z" } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

**Napomena:** trenutno vraća i arhivirane navike (nema filtera). Vidi [Poznata ograničenja](#poznata-ograničenja).

## 3. Jedna navika po id

```
GET /habits/{id}
```

| Path param | Tip | Opis |
|------------|-----|------|
| `id` | `long` | Identifikator navike |

**Odgovor:** `200 OK`, `HabitResponse`.

| Status | Uslov |
|--------|-------|
| `200` | Pronađena |
| `404` | Ne postoji |

## 4. Izmeni naviku

```
PUT /habits/{id}
```

Telo zahteva:

| Polje | Tip | Ograničenja |
|-------|-----|-------------|
| `version` | `long` | `@NotNull` — trenutna verzija navike (optimistic locking) |
| `name` | `string` | `@NotBlank`, `@Size(max = 255)` — novi naziv |

```json
{ "version": 3, "name": "Read 45 min" }
```

**Odgovor:** `200 OK`, `HabitResponse` (sa uvećanom `version` u bazi).

| Status | Uslov |
|--------|-------|
| `200` | Izmenjeno |
| `400` | Validacija pala / pokvareno telo |
| `404` | Ne postoji |
| `409` | `version` iz zahteva ne odgovara verziji u bazi (neko je u međuvremenu izmenio) |

## 5. Obriši naviku

```
DELETE /habits/{id}
```

| Path param | Tip | Opis |
|------------|-----|------|
| `id` | `long` | Identifikator navike |

**Odgovor:** `204 No Content`, bez tela.

| Status | Uslov |
|--------|-------|
| `204` | Obrisano |
| `404` | Ne postoji (uključujući ponovljeni `DELETE` već obrisane navike) |

## 6. Označi kao odrađeno

```
POST /habits/{id}/complete
```

Bez tela. Datum se uzima serverski (`LocalDate.now()`). Uvećava `completionCount` i ažurira `currentStreak`/`longestStreak`. Upisuje red u istoriju i emituje `HabitCompletedEvent` na Kafka topic `habit-completed`.

**Ponašanje:** ponovni `complete` istog dana je no-op — bez duplog reda, bez novog eventa, streak se ne menja.

**Odgovor:** `200 OK`, `HabitResponse`.

| Status | Uslov |
|--------|-------|
| `200` | Označeno (ili no-op ako je već označeno danas) |
| `400` | Navika je arhivirana |
| `404` | Ne postoji |

## 7. Poništi današnje označavanje

```
POST /habits/{id}/uncomplete
```

Bez tela. Briše današnji red iz istorije, vraća `completionCount`/`currentStreak`/`lastCompletedAt` na prethodno stanje i emituje `HabitUncompletedEvent`.

**Odgovor:** `200 OK`, `HabitResponse`.

| Status | Uslov |
|--------|-------|
| `200` | Poništeno |
| `400` | Navika nije bila odrađena danas, ili je arhivirana |
| `404` | Ne postoji |

## 8. Arhiviraj naviku

```
POST /habits/{id}/archive
```

Bez tela. Postavlja `archived = true` (soft delete — navika i istorija ostaju u bazi). Arhivirana navika ne može da se `complete`/`uncomplete` (vraća `400`).

**Ponašanje:** idempotentno — ponovni `archive` je no-op.

**Odgovor:** `200 OK`, `HabitResponse`.

| Status | Uslov |
|--------|-------|
| `200` | Arhivirano |
| `404` | Ne postoji |

## 9. Vrati iz arhive

```
POST /habits/{id}/unarchive
```

Bez tela. Postavlja `archived = false`.

**Ponašanje:** idempotentno — ponovni `unarchive` je no-op.

**Odgovor:** `200 OK`, `HabitResponse`.

| Status | Uslov |
|--------|-------|
| `200` | Vraćeno iz arhive |
| `404` | Ne postoji |

## 10. Istorija odrađenih dana

```
GET /habits/{id}/history
```

| Path param | Tip | Opis |
|------------|-----|------|
| `id` | `long` | Identifikator navike |

Čita se direktno iz write-side tabele `habit_completions` (uvek tačno, sinhrono). Sortirano opadajuće po datumu.

**Odgovor:** `200 OK`, niz `HabitCompletionResponse`.

```json
[
  { "id": 105, "completedOn": "2026-06-25" },
  { "id": 98, "completedOn": "2026-06-24" }
]
```

| Status | Uslov |
|--------|-------|
| `200` | OK (prazan niz `[]` ako nema odrađenih dana) |
| `404` | Ne postoji |

**Napomena:** nije paginirano — vraća sve dane. Vidi [Poznata ograničenja](#poznata-ograničenja).

## 11. Agregatna statistika

```
GET /habits/{id}/stats
```

| Path param | Tip | Opis |
|------------|-----|------|
| `id` | `long` | Identifikator navike |

Čita se iz read-modela `habit_completion_stats` (Kafka projekcija).

**Odgovor:** `200 OK`, `HabitStatsResponse`.

```json
{ "completionCount": 7, "longestStreak": 5, "lastCompletedOn": "2026-06-25", "currentStreak": 4 }
```

| Status | Uslov |
|--------|-------|
| `200` | OK (nule / `null` ako nema podataka) |
| `404` | Ne postoji |

**Ponašanje:**
- **Eventualna konzistencija** — read-model se puni asinhrono preko Kafke. Poziv odmah nakon `complete` može vratiti staro stanje dok consumer ne obradi event.
- **`currentStreak` se koriguje na vreme čitanja** — read-model čuva streak iz trenutka poslednjeg completa. Ako je poslednji complete bio danas ili juče, vraća se sačuvana vrednost; inače `0` (streak je istekao a da nije stigao event da ga obori).

---

## Poznata ograničenja

Otvorene stavke — kandidati za dopunu (ažurirati kako se rešavaju):

| Stavka | Opis |
|--------|------|
| Filter arhive na listi | `GET /habits` (endpoint 2) vraća i arhivirane. Predlog: `?includeArchived=false` default + filter u query-ju (`findByArchivedFalse`). |
| `archived` nije u odgovoru | `HabitResponse` ne izlaže `archived`, pa klijent ne vidi rezultat `archive`/`unarchive` direktno. |
| `id` u istoriji | `HabitCompletionResponse.id` (endpoint 10) nijedan endpoint ne prima nazad kao ulaz — kandidat za uklanjanje. |
| Istorija bez paginacije | `GET /habits/{id}/history` (endpoint 10) vraća sve redove; raste neograničeno. |
