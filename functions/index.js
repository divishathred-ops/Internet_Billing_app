const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

/**
 * Helper: Get start and end timestamps for today
 */
function getTodayRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);
  return { start: admin.firestore.Timestamp.fromDate(start), end: admin.firestore.Timestamp.fromDate(end) };
}

/**
 * Helper: Get start and end timestamps for the current month
 */
function getCurrentMonthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);
  return { start: admin.firestore.Timestamp.fromDate(start), end: admin.firestore.Timestamp.fromDate(end) };
}

/**
 * Cloud Function: getTotals
 *
 * This function automatically gets the authenticated user's UID from the request context.
 * No need to pass uid as a parameter anymore.
 */
exports.getTotals = functions.https.onCall(async (data, context) => {
  try {
    // Verify user is authenticated
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
    }

    const uid = context.auth.uid;
    const role = data.role;

    if (!role) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing role parameter');
    }

    console.log(`getTotals called by uid: ${uid}, role: ${role}`);

    const { start: todayStart, end: todayEnd } = getTodayRange();
    const { start: monthStart, end: monthEnd } = getCurrentMonthRange();

    // Build base query for collectedPayment transactions
    let baseQuery = db.collectionGroup('balanceSheet')
      .where('type', '==', 'collectedPayment');

    // Admin sees all where adminUid == uid
    // Agent sees only where createdBy == uid
    if (role === 'admin') {
      baseQuery = baseQuery.where('adminUid', '==', uid);
    } else if (role === 'agent') {
      baseQuery = baseQuery.where('createdBy', '==', uid);
    } else {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid role. Must be admin or agent');
    }

    // Fetch today's transactions
    const todaySnapshot = await baseQuery
      .where('date', '>=', todayStart)
      .where('date', '<=', todayEnd)
      .get();

    let todaySum = 0.0;
    todaySnapshot.forEach(doc => {
      todaySum += Number(doc.get('amount') || 0);
    });

    // Fetch month-to-date transactions
    const monthSnapshot = await baseQuery
      .where('date', '>=', monthStart)
      .where('date', '<=', monthEnd)
      .get();

    let monthSum = 0.0;
    monthSnapshot.forEach(doc => {
      monthSum += Number(doc.get('amount') || 0);
    });

    console.log(`getTotals result for ${uid}: today=${todaySum}, month=${monthSum}`);

    return {
      today: todaySum,
      month: monthSum
    };

  } catch (error) {
    console.error('getTotals error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

/**
 * Cloud Function: getCollectionDetails
 */
exports.getCollectionDetails = functions.https.onCall(async (data, context) => {
  try {
    // Verify user is authenticated
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
    }

    const uid = context.auth.uid;
    const role = data.role;
    const startMillis = Number(data.start);
    const endMillis = Number(data.end);

    if (!role || !startMillis || !endMillis) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters: role, start, end');
    }

    console.log(`getCollectionDetails called by uid: ${uid}, role: ${role}, range: ${startMillis}-${endMillis}`);

    const start = admin.firestore.Timestamp.fromMillis(startMillis);
    const end = admin.firestore.Timestamp.fromMillis(endMillis);

    // Base query for collectedPayment
    let query = db.collectionGroup('balanceSheet')
      .where('type', '==', 'collectedPayment')
      .where('date', '>=', start)
      .where('date', '<=', end);

    if (role === 'admin') {
      query = query.where('adminUid', '==', uid);
    } else if (role === 'agent') {
      query = query.where('createdBy', '==', uid);
    } else {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid role. Must be admin or agent');
    }

    const snapshot = await query.orderBy('date', 'desc').get();

    const transactions = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      // Convert Firestore timestamp to milliseconds for easier handling
      date: doc.data().date.toMillis()
    }));

    console.log(`getCollectionDetails result for ${uid}: ${transactions.length} transactions`);

    return { transactions };

  } catch (error) {
    console.error('getCollectionDetails error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});
