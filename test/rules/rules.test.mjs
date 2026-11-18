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
  arrayRemove,
  collection,
  deleteDoc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  where,
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

// Every person signs in with Google; an anonymous account (from before
// that) has no identities until the app links one.
const db = (uid) => env.authenticatedContext(uid, {
  firebase: { sign_in_provider: 'google.com', identities: { 'google.com': [`g-${uid}`] } },
}).firestore();
const anonymousAccount = (uid) => env.authenticatedContext(uid, {
  firebase: { sign_in_provider: 'anonymous', identities: {} },
}).firestore();
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

  test('the creator sets up the member list with the trip', async () => {
    const f = db('alice');
    const b = writeBatch(f);
    b.set(doc(f, 'trips/t1'), trip('alice'));
    b.set(doc(f, 'trips/t1/members/alice'), { name: 'A', uid: 'alice', order: 0, joinedAt: 1 });
    b.set(doc(f, 'trips/t1/members/p1'), { name: 'B', uid: null, order: 1, joinedAt: 1 });
    await assertSucceeds(b.commit());
  });

  test('only the owner changes who is in, and stays in', async () => {
    await seedTrip(['alice', 'bob', 'carol']);
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { memberUids: arrayRemove('carol') }));
    await assertSucceeds(updateDoc(doc(db('alice'), 'trips/t1'),
      { memberUids: arrayRemove('carol') }));
    await assertFails(updateDoc(doc(db('alice'), 'trips/t1'),
      { memberUids: arrayRemove('alice') }));
  });

  test('only the owner dissolves the trip', async () => {
    await seedTrip(['alice', 'bob']);
    await assertFails(deleteDoc(doc(db('bob'), 'trips/t1')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'trips/t1')));
  });
});

describe('the member list', () => {
  beforeEach(async () => {
    await seedTrip(['alice', 'bob']);
    await seed((f) => setDoc(doc(f, 'trips/t1/members/p1'),
      { name: '阿嬤', uid: null, order: 2, joinedAt: 1 }));
  });

  test('only the owner adds and removes members', async () => {
    const add = { name: 'C', uid: null, order: 3, joinedAt: 1 };
    await assertFails(setDoc(doc(db('bob'), 'trips/t1/members/p2'), add));
    await assertSucceeds(setDoc(doc(db('alice'), 'trips/t1/members/p2'), add));
    await assertFails(deleteDoc(doc(db('bob'), 'trips/t1/members/p1')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'trips/t1/members/p1')));
  });

  test('members rename only themselves; the owner renames anyone', async () => {
    await assertSucceeds(updateDoc(doc(db('bob'), 'trips/t1/members/bob'), { name: 'B' }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1/members/p1'), { name: 'X' }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1/members/bob'), { uid: 'carol' }));
    await assertSucceeds(updateDoc(doc(db('alice'), 'trips/t1/members/p1'), { name: '奶奶' }));
  });

  test('the owner frees a place but cannot hand it to someone', async () => {
    await assertSucceeds(updateDoc(doc(db('alice'), 'trips/t1/members/bob'), { uid: null }));
    await assertFails(updateDoc(doc(db('alice'), 'trips/t1/members/p1'), { uid: 'mallory' }));
  });

  test('a member moves to an open place, never a taken one', async () => {
    const f = db('bob');
    const b = writeBatch(f);
    b.update(doc(f, 'trips/t1/members/bob'), { uid: null });
    b.update(doc(f, 'trips/t1/members/p1'), { uid: 'bob', joinedAt: 2 });
    await assertSucceeds(b.commit());
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1/members/alice'), { uid: 'bob' }));
    await assertFails(updateDoc(doc(db('carol'), 'trips/t1/members/bob'), { uid: 'carol' }));
  });

  test('a member lets go of only their own place', async () => {
    await assertSucceeds(updateDoc(doc(db('bob'), 'trips/t1/members/bob'), { uid: null }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1/members/alice'), { uid: null }));
  });
});

describe('leaving and handing over', () => {
  function leave(uid) {
    const f = db(uid);
    const b = writeBatch(f);
    b.update(doc(f, `trips/t1/members/${uid}`), { uid: null });
    b.update(doc(f, 'trips/t1'), { memberUids: arrayRemove(uid) });
    return b.commit();
  }

  test('a member leaves; their name stays as an open place', async () => {
    await seedTrip(['alice', 'bob', 'carol']);
    await assertSucceeds(leave('bob'));
    await assertFails(getDoc(doc(db('bob'), 'trips/t1')));
    const left = await getDoc(doc(db('alice'), 'trips/t1/members/bob'));
    if (left.data().uid !== null || left.data().name !== 'bob') throw new Error('place not kept');
  });

  test('leaving takes out only yourself, and the owner cannot leave', async () => {
    await seedTrip(['alice', 'bob', 'carol']);
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { memberUids: ['alice'] }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { memberUids: arrayRemove('bob'), name: 'x' }));
    await assertFails(leave('alice'));
  });

  test('the owner hands the trip to a member, not an outsider', async () => {
    await seedTrip(['alice', 'bob']);
    await assertFails(updateDoc(doc(db('alice'), 'trips/t1'), { ownerUid: 'mallory' }));
    await assertSucceeds(updateDoc(doc(db('alice'), 'trips/t1'),
      { ownerUid: 'bob', ownerSeenAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db('alice'), 'trips/t1'), { ownerUid: 'alice' }));
    await assertSucceeds(leave('alice'));
  });

  test('only the owner stamps ownerSeenAt, and only with now', async () => {
    await seedTrip(['alice', 'bob']);
    await assertSucceeds(updateDoc(doc(db('alice'), 'trips/t1'), { ownerSeenAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db('alice'), 'trips/t1'), { ownerSeenAt: inHours(-24 * 30) }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'), { ownerSeenAt: inHours(-24 * 30) }));
  });

  test('a member takes over an owner who never stamped the trip', async () => {
    await seedTrip(['alice', 'bob']);
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { ownerUid: 'bob', ownerSeenAt: inHours(0) }));
    await assertFails(updateDoc(doc(db('carol'), 'trips/t1'),
      { ownerUid: 'carol', ownerSeenAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db('bob'), 'trips/t1'),
      { ownerUid: 'bob', ownerSeenAt: serverTimestamp() }));
  });

  test('a member takes over only after 14 days without the owner', async () => {
    await seedTrip(['alice', 'bob']);
    await seed((f) => updateDoc(doc(f, 'trips/t1'), { ownerSeenAt: inHours(-24 * 13) }));
    await assertFails(updateDoc(doc(db('bob'), 'trips/t1'),
      { ownerUid: 'bob', ownerSeenAt: serverTimestamp() }));
    await seed((f) => updateDoc(doc(f, 'trips/t1'), { ownerSeenAt: inHours(-24 * 15) }));
    await assertSucceeds(updateDoc(doc(db('bob'), 'trips/t1'),
      { ownerUid: 'bob', ownerSeenAt: serverTimestamp() }));
  });
});

describe('joining with an invite', () => {
  async function inviteFor(tripId, hours) {
    await seed((f) => setDoc(doc(f, 'invites/ABCD2345'),
      { tripId, expiresAt: inHours(hours), createdBy: 'alice' }));
  }

  async function seedTripWithPlace() {
    await seedTrip();
    await seed((f) => setDoc(doc(f, 'trips/t1/members/p1'),
      { name: 'Bob', uid: null, order: 1, joinedAt: 1 }));
  }

  function join(uid, place = 'p1') {
    const f = db(uid);
    const b = writeBatch(f);
    b.update(doc(f, 'trips/t1'), { memberUids: arrayUnion(uid) });
    b.update(doc(f, `trips/t1/members/${place}`), { uid, joinedAt: 2 });
    return b.commit();
  }

  const openPlaces = (uid) => getDocs(query(
    collection(db(uid), 'trips/t1/members'), where('uid', '==', null)));

  test('a valid invite lets someone see open places and take one', async () => {
    await seedTripWithPlace();
    await inviteFor('t1', 24);
    await assertSucceeds(getDoc(doc(db('bob'), 'invites/ABCD2345')));
    await assertFails(openPlaces('bob'));
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertSucceeds(openPlaces('bob'));
    await assertSucceeds(join('bob'));
    await assertSucceeds(getDoc(doc(db('bob'), 'trips/t1')));
  });

  test('a taken place cannot be taken again', async () => {
    await seedTripWithPlace();
    await inviteFor('t1', 24);
    await setDoc(doc(db('carol'), 'users/carol'), { joinCode: 'ABCD2345' });
    await assertFails(join('carol', 'alice'));
  });

  test('joining without taking a place is refused', async () => {
    await seedTripWithPlace();
    await inviteFor('t1', 24);
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    const f = db('bob');
    const b = writeBatch(f);
    b.set(doc(f, 'trips/t1/members/bob'), { name: 'bob', uid: 'bob', order: 9, joinedAt: 1 });
    b.update(doc(f, 'trips/t1'), { memberUids: arrayUnion('bob') });
    await assertFails(b.commit());
  });

  test('no invite code, no join', async () => {
    await seedTripWithPlace();
    await setDoc(doc(db('bob'), 'users/bob'), { nickname: 'bob' });
    await assertFails(join('bob'));
  });

  test('an expired invite is refused', async () => {
    await seedTripWithPlace();
    await inviteFor('t1', -1);
    await setDoc(doc(db('bob'), 'users/bob'), { joinCode: 'ABCD2345' });
    await assertFails(join('bob'));
  });

  test('an invite for another trip is refused', async () => {
    await seedTripWithPlace();
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
  // Members have rules of their own, under 'the member list'.
  for (const sub of ['expenses', 'settlements', 'items', 'checklist',
                     'flights', 'lodgings']) {
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
});

describe('users', () => {
  test('each device owns only its own document', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'users/alice'), { nickname: 'A' }));
    await assertFails(setDoc(doc(db('bob'), 'users/alice'), { nickname: 'B' }));
    await assertFails(getDoc(doc(db('bob'), 'users/alice')));
  });
});

describe('error reports', () => {
  const report = (uid, extra = {}) => ({
    uid, kind: 'feedback', message: '結算怪怪的', version: '0.1.0+9 web-iOS',
    createdAt: serverTimestamp(), ...extra,
  });

  test('anyone signed in can send one for themselves', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'errorReports/r1'), report('alice')));
    await assertSucceeds(setDoc(doc(db('alice'), 'errorReports/r2'),
      report('alice', { kind: 'error', stack: 'at main.dart' })));
    await assertFails(setDoc(doc(anon(), 'errorReports/r3'), report('alice')));
    await assertFails(setDoc(doc(db('bob'), 'errorReports/r4'), report('alice')));
  });

  test('reports are bounded and cannot be read back or changed', async () => {
    await assertFails(setDoc(doc(db('alice'), 'errorReports/r1'),
      report('alice', { message: 'x'.repeat(2001) })));
    await assertFails(setDoc(doc(db('alice'), 'errorReports/r2'),
      report('alice', { kind: 'spam' })));
    await assertFails(setDoc(doc(db('alice'), 'errorReports/r3'),
      report('alice', { extra: 1 })));
    await assertSucceeds(setDoc(doc(db('alice'), 'errorReports/r4'), report('alice')));
    await assertFails(getDoc(doc(db('alice'), 'errorReports/r4')));
    await assertFails(updateDoc(doc(db('alice'), 'errorReports/r4'), { message: 'x' }));
    await assertFails(deleteDoc(doc(db('alice'), 'errorReports/r4')));
  });
});

describe('anonymous accounts', () => {
  test('are refused everywhere until linked to Google', async () => {
    await seedTrip(['alice']);
    await assertFails(getDoc(doc(anonymousAccount('alice'), 'trips/t1')));
    await assertFails(getDoc(doc(anonymousAccount('alice'), 'trips/t1/expenses/e1')));
    await assertFails(setDoc(doc(anonymousAccount('carol'), 'trips/t2'), trip('carol')));
    await assertFails(setDoc(doc(anonymousAccount('carol'), 'users/carol'), { nickname: 'C' }));
    // Linking keeps the uid, so the same person reads the trip again.
    await assertSucceeds(getDoc(doc(db('alice'), 'trips/t1')));
  });
});
