enum SplitMode { equal, exact, shares }

enum ExpenseCategory { food, transport, lodging, ticket, shopping, other }

extension ExpenseCategoryLabel on ExpenseCategory {
  String get label => switch (this) {
        ExpenseCategory.food => '餐飲',
        ExpenseCategory.transport => '交通',
        ExpenseCategory.lodging => '住宿',
        ExpenseCategory.ticket => '門票',
        ExpenseCategory.shopping => '購物',
        ExpenseCategory.other => '其他',
      };
}

/// One payment by one member, split among some members.
class Expense {
  const Expense({
    required this.id,
    required this.title,
    required this.category,
    required this.amountMinor,
    required this.currency,
    required this.rateToBase,
    required this.payerId,
    required this.splitMode,
    required this.splits,
    required this.occurredAt,
    required this.createdBy,
    required this.updatedAt,
    this.note,
  });

  final String id;
  final String title;
  final ExpenseCategory category;

  /// In [currency]'s minor units.
  final int amountMinor;
  final String currency;

  /// One unit of [currency] in the trip's base currency, as a decimal
  /// string ("0.2134"), fixed when the expense was recorded. A string, not a
  /// double, so settling never inherits binary rounding error.
  final String rateToBase;
  final String payerId;
  final SplitMode splitMode;

  /// memberId → weight. equal: 1 per participant; exact: that member's
  /// amount in [currency] minor units; shares: number of shares.
  final Map<String, int> splits;
  final int occurredAt;
  final String? note;
  final String createdBy;
  final int updatedAt;

  Map<String, dynamic> toMap() => {
        'title': title,
        'category': category.name,
        'amountMinor': amountMinor,
        'currency': currency,
        'rateToBase': rateToBase,
        'payerId': payerId,
        'splitMode': splitMode.name,
        'splits': splits,
        'occurredAt': occurredAt,
        'note': note,
        'createdBy': createdBy,
        'updatedAt': updatedAt,
      };

  factory Expense.fromMap(String id, Map<String, dynamic> m) => Expense(
        id: id,
        title: m['title'] as String,
        category: ExpenseCategory.values.asNameMap()[m['category']] ??
            ExpenseCategory.other,
        amountMinor: m['amountMinor'] as int,
        currency: m['currency'] as String,
        rateToBase: m['rateToBase'] as String,
        payerId: m['payerId'] as String,
        splitMode: SplitMode.values.byName(m['splitMode'] as String),
        splits: Map<String, int>.from(m['splits'] as Map),
        occurredAt: m['occurredAt'] as int,
        note: m['note'] as String?,
        createdBy: m['createdBy'] as String,
        updatedAt: m['updatedAt'] as int,
      );
}

/// A repayment someone marked as done, in the trip's base currency.
class Settlement {
  const Settlement({
    required this.id,
    required this.fromId,
    required this.toId,
    required this.amountMinor,
    required this.at,
    required this.createdBy,
  });

  final String id;
  final String fromId;
  final String toId;
  final int amountMinor;
  final int at;
  final String createdBy;

  Map<String, dynamic> toMap() => {
        'fromId': fromId,
        'toId': toId,
        'amountMinor': amountMinor,
        'at': at,
        'createdBy': createdBy,
      };

  factory Settlement.fromMap(String id, Map<String, dynamic> m) => Settlement(
        id: id,
        fromId: m['fromId'] as String,
        toId: m['toId'] as String,
        amountMinor: m['amountMinor'] as int,
        at: m['at'] as int,
        createdBy: m['createdBy'] as String,
      );
}
