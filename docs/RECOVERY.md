# Library Tablet Recovery Guide / የመልሶ ማግኛ መመሪያ

Print this page and keep it at the library desk.
ይህን ገጽ አትመው በቤተ መጻሕፍቱ ጠረጴዛ ላይ ያስቀምጡ።

---

## English

**If the tablet is lost, broken, or replaced, the library's data is safe in
the cloud.** To bring it back onto a new tablet you need:

- a new Android tablet with internet,
- the library app (APK) installed on it,
- the library account **email and password** (kept in the family password
  manager / with the maintainer).

**Steps:**

1. Install the library app on the new tablet and open it.
2. Go to **Settings** (enter the staff PIN).
3. In the **Cloud backup** section, **Sign in** with the library email and
   password.
4. Tap **Restore from cloud** and confirm.
5. Wait for the message **"Restore complete"**.
6. Open **Statistics** and check the totals (books, members, loans) look
   right.
7. Do one test: check a book out and return it.

If restore fails with a network message, connect to internet and try again.
If it fails with an account message, the sign-in email/password is wrong or
the account is not the library's own account.

---

## አማርኛ (Amharic)

**ጡባዊው ቢጠፋ፣ ቢሰበር ወይም ቢቀየር — የቤተ መጻሕፍቱ መረጃ በደመና ተጠብቆ ይገኛል።**
ወደ አዲስ ጡባዊ መልሶ ለማምጣት የሚያስፈልጉ ነገሮች፦

- በይነመረብ ያለው አዲስ የአንድሮይድ ጡባዊ፣
- የቤተ መጻሕፍቱ መተግበሪያ (APK) በላዩ ላይ ተጭኖ፣
- የቤተ መጻሕፍቱ መለያ **ኢሜይል እና የይለፍ ቃል**።

**ደረጃዎች፦**

1. የቤተ መጻሕፍቱን መተግበሪያ በአዲሱ ጡባዊ ላይ ይጫኑ እና ይክፈቱት።
2. ወደ **ቅንብሮች** ይሂዱ (የሠራተኛ ፒን ያስገቡ)።
3. በ**የደመና ምትኬ** ክፍል ውስጥ በቤተ መጻሕፍቱ ኢሜይል እና የይለፍ ቃል **ይግቡ**።
4. **ከደመና መልስ** የሚለውን ተጭነው ያረጋግጡ።
5. **"መልሶ ማግኘት ተጠናቋል"** የሚል መልእክት እስኪመጣ ይጠብቁ።
6. ወደ **ስታቲስቲክስ** ገጽ ሄደው ቁጥሮቹ (መጻሕፍት፣ አባላት፣ ውሰቶች) ትክክል መሆናቸውን
   ያረጋግጡ።
7. ለሙከራ አንድ መጽሐፍ አውሰው ይመልሱ።

መልሶ ማግኘቱ በበይነመረብ ችግር ካልተሳካ፣ በይነመረብ ተገናኝተው እንደገና ይሞክሩ።
በመለያ ችግር ካልተሳካ፣ ኢሜይሉ/የይለፍ ቃሉ የተሳሳተ ነው ወይም መለያው የቤተ መጻሕፍቱ
አይደለም።

---

*Maintainer notes: Firebase project `ethiopia-library`, Firestore collections
categories / books / book_copies / members / loans / config. The exported
`library-backup-*.db` files (Settings → Export backup file) are plain SQLite
copies — a last-resort readable with any SQLite tool, not an in-app import.
The in-app recovery path is the cloud restore above. Restore was
device-verified as part of Wave 2 of the 2026-07 redesign plan.*
