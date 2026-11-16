import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../logic/split.dart';
import '../models/currency.dart';
import '../models/expense.dart';
import '../theme.dart';
import 'expense_form_screen.dart';
import 'trip_screen.dart';

class ExpensesTab extends StatelessWidget {
  const ExpensesTab({super.key, required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    final base = Currency.of(data.trip.baseCurrency);
    final total = data.expenses.fold<int>(0, (s, e) => s + baseTotal(e, base));
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'add-expense',
        onPressed: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => ExpenseFormScreen(data: data))),
        icon: const Icon(Icons.add),
        label: const Text('記一筆'),
      ),
      body: data.expenses.isEmpty
          ? const Center(child: Text('還沒有支出，按右下角記第一筆'))
          : ListView(
              padding: const EdgeInsets.only(top: 8, bottom: 96),
              children: [
                Card(
                  color: AppColors.yamabuki,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      '總花費 ${base.format(total)} ${base.code}',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                ),
                for (final e in data.expenses) _ExpenseTile(data: data, e: e),
              ],
            ),
    );
  }
}

class _ExpenseTile extends StatelessWidget {
  const _ExpenseTile({required this.data, required this.e});
  final TripData data;
  final Expense e;

  @override
  Widget build(BuildContext context) {
    final cur = Currency.of(e.currency);
    final base = Currency.of(data.trip.baseCurrency);
    final when = DateFormat('M/d HH:mm')
        .format(DateTime.fromMillisecondsSinceEpoch(e.occurredAt));
    final foreign = e.currency != base.code;
    return Card(
      child: ListTile(
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => ExpenseFormScreen(data: data, existing: e))),
        leading: CircleAvatar(
          backgroundColor: _categoryColor(e.category),
          foregroundColor: AppColors.ink,
          child: Text(e.category.label.substring(0, 1)),
        ),
        title: Text(e.title),
        subtitle: Text('${data.nameOf(e.payerId)} 付　·　$when'),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text('${cur.format(e.amountMinor)} ${cur.code}',
                style: Theme.of(context).textTheme.titleMedium),
            if (foreign)
              Text('≈ ${base.format(baseTotal(e, base))} ${base.code}',
                  style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

Color _categoryColor(ExpenseCategory c) => switch (c) {
      ExpenseCategory.food => AppColors.coral,
      ExpenseCategory.transport => AppColors.mizu,
      ExpenseCategory.lodging => AppColors.wakatake,
      ExpenseCategory.ticket => AppColors.yamabuki,
      ExpenseCategory.shopping => AppColors.coral,
      ExpenseCategory.other => Colors.white,
    };
