// Security rules tests, run against the Firestore emulator:
//   cd test/rules && npm ci && npm test
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  arrayUnion,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'tripshare-f6209',
    firestore: { rules: readFileSync('firestore.rules', 'utf8') },
  });
});

after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const trip = (owner, members = [owner]) => ({
  name: '東京',
  baseCurrency: 'TWD',
  startDate: '2026-12-20',
  endDate: '2026-12-24',
  ownerUid: owner,
  memberUids: members,
  createdAt: 1,
});

/** Writes data as an admin, bypassing the rules. */
async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

async function seedTrip(members = ['alice']) {
  await seed(async (f) => {
    await setDoc(doc(f, 'trips/t1'), trip(members[0], members));
    for (const [i, uid] of members.entries()) {
      await setDoc(doc(f, `trips/t1/members/${uid}`),
        { name: uid, uid, order: i, joinedAt: 1 });
    }
  });
}

const inHours = (h) => Timestamp.fromMillis(Date.now() + h * 3600e3);

describe('trips', () => {
  test('anyone signed in can create a trip they own alone', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'trips/t1'), trip('alice')));
    await assertFails(setDoc(doc(anon(), 'trips/t2'), trip('alice')));
    await assertFails(setDoc(doc(db('alice'), 'trips/t3'), trip('bob')));
    await assertFails(
      setDoc(doc(db('alice'), 'trips/t4'), trip('alice', ['alice', 'bob'])));
  });

  test('only members can read a trip', async () => {
    await seedTrip(['alice']);
    await assertSucceeds(getDoc(doc(db('alice'), 'trips/t1')));
    await assertFails(getDoc(doc(db('bob'), 'trips/t1')));
  });

  test('members can edit but not take the trip or drop others', async () => {
    await seedTrip(['alice', 'bob']);
    await assertSucceeds(updateDoc(doc(db('bob'), 'trips/t1'), { name: '大阪' }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'), { ownerUid: 'bob' }));
    await assertFails(
      updateDoc(doc(db('bob'), 'trips/t1'), { memberUids: ['bob'] }));
  });
});

describe('joining with an invite', () => {
  async function inviteFor(tripId, hours) {
    await seed((f) => setDoc(doc(f, 'invites/ABCD2345'),
      { tripId, expiresAt: inHours(hours), createdBy: 'alice' }));
  }

  function join(uid) {
    const f = db(uid);
    const b = writeBatch(f);
    b.update(doc(f, 'trips/t1'), { memberUids: arrayUnion(uid) });
    b.set(doc(f, `trips/t1/members/${uid}`),
      { name: uid, uid, order: 9, joinedAt: 1 });
    return b.commit();
  }

  test('a valid invite lets someone join', async () => {
    await seedTrip();
    await inviteFor('t1', 24);
    await assertSucceeds(getDoc(doc(db('bob'), 'invites/ABCD2345')));
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertSucceeds(join('bob'));
    await assertSucceeds(getDoc(doc(db('bob'), 'trips/t1')));
  });

  test('no invite code, no join', async () => {
    await seedTrip();
    await setDoc(doc(db('bob'), 'users/bob'), { nickname: 'bob' });
    await assertFails(join('bob'));
  });

  test('an expired invite is refused', async () => {
    await seedTrip();
    await inviteFor('t1', -1);
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertFails(join('bob'));
  });

  test('an invite for another trip is refused', async () => {
    await seedTrip();
    await inviteFor('other', 24);
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertFails(join('bob'));
  });

  test('joining cannot add someone else or change the trip', async () => {
    await seedTrip();
    await inviteFor('t1', 24);
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { memberUids: arrayUnion('bob', 'carol') }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { memberUids: arrayUnion('bob'), name: 'mine' }));
  });

  test('only members create invites, and only for their trip', async () => {
    await seedTrip();
    const invite = { tripId: 't1', expiresAt: inHours(24), createdBy: 'alice' };
    await assertSucceeds(setDoc(doc(db('alice'), 'invites/AAAA2222'), invite));
    await assertFails(setDoc(doc(db('bob'), 'invites/BBBB2222'),
      { ...invite, createdBy: 'bob' }));
  });
});

describe('inside a trip', () => {
  for (const sub of ['expenses', 'settlements', 'items', 'checklist',
                     'flights', 'lodgings', 'members']) {
    test(`members read and write ${sub}; others cannot`, async () => {
      await seedTrip(['alice']);
      await assertSucceeds(setDoc(doc(db('alice'), `trips/t1/${sub}/x`), { a: 1 }));
      await assertSucceeds(getDoc(doc(db('alice'), `trips/t1/${sub}/x`)));
      await assertFails(getDoc(doc(db('bob'), `trips/t1/${sub}/x`)));
      await assertFails(setDoc(doc(db('bob'), `trips/t1/${sub}/y`), { a: 1 }));
    });
  }

  test('unknown collections stay closed', async () => {
    await seedTrip(['alice']);
    await assertFails(setDoc(doc(db('alice'), 'trips/t1/secrets/x'), { a: 1 }));
  });

  test('a member can claim a name-only member', async () => {
    await seedTrip(['alice', 'bob']);
    await seed((f) => setDoc(doc(f, 'trips/t1/members/grandma'),
      { name: '阿嬤', uid: null, order: 2, joinedAt: 1 }));
    const f = db('bob');
    const b = writeBatch(f);
    b.update(doc(f, 'trips/t1/members/grandma'), { uid: 'bob' });
    b.delete(doc(f, 'trips/t1/members/bob'));
    await assertSucceeds(b.commit());
  });
});

describe('users', () => {
  test('each device owns only its own document', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'users/alice'), { nickname: 'A' }));
    await assertFails(setDoc(doc(db('bob'), 'users/alice'), { nickname: 'B' }));
    await assertFails(getDoc(doc(db('bob'), 'users/alice')));
  });
});
