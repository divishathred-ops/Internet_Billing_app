// functions/monthlyBilling.js

const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

// One-off run: June 20, 2025 at midnight Europe/Paris
// One-off run: June 22, 2025 at midnight IST
exports.charge22June = functions.pubsub
  .schedule('0 0 22 6 *')
  .timeZone('Asia/Kolkata')
  .onRun(async (context) => {
    await runBillingCycle();
    return null;
  });

// Monthly run: 1st of every month at midnight IST
exports.charge1stMonthly = functions.pubsub
  .schedule('0 0 1 * *')
  .timeZone('Asia/Kolkata')
  .onRun(async (context) => {
    await runBillingCycle();
    return null;
  });


async function runBillingCycle() {
  const now = admin.firestore.Timestamp.now();
  const customersSnap = await db.collection('customers').get();
  const batch = db.batch();

  customersSnap.docs.forEach((doc) => {
    const data = doc.data();
    const amount = data.recurringCharge;
    if (typeof amount === 'number' && amount > 0) {
      const txRef = doc.ref.collection('balanceSheet').doc();
      batch.set(txRef, {
        id: txRef.id,
        customerId: doc.id,
        amount: amount,
        date: now,
        type: 'generatedDue',
        description: `Monthly charge: ₹${amount}`,
        agentId: null
      });
      batch.update(doc.ref, {
        balance: admin.firestore.FieldValue.increment(amount),
        lastPaymentDate: now
      });
    }
  });

  await batch.commit();
  console.log(`Applied billing cycle to ${customersSnap.size} customers.`);
}
