import 'package:flutter/material.dart';

import '../logic/split.dart';
import '../main.dart';
import '../models/currency.dart';
import '../theme.dart';
import 'trip_screen.dart';

class SettleTab extends StatelessWidget {
  const SettleTab({super.key, required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    final base = Currency.of(data.trip.baseCurrency);
    final net = balances(
      members: data.members,
      expenses: data.expenses,
      settlements: data.settlements,
      base: base,
    );
    final transfers = suggestTransfers(net);
    final repo = AppScope.of(context).repo;
    final text = Theme.of(context).textTheme;
    String money(int v) => '${base.format(v)} ${base.code}';

    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        const _Heading('每個人的狀況'),
        for (final m in data.members)
          Card(
            child: ListTile(
              title: Text(m.name),
              trailing: Text(
                net[m.id]! == 0
                    ? '剛好'
                    : net[m.id]! > 0
                        ? '應收 ${money(net[m.id]!)}'
                        : '應付 ${money(-net[m.id]!)}',
                style: text.titleMedium,
              ),
            ),
          ),
        const _Heading('怎麼轉帳'),
        if (transfers.isEmpty)
          const Card(
            child: ListTile(title: Text('大家都結清了 🎉')),
          ),
        for (final t in transfers)
          Card(
            color: AppColors.mizu,
            child: ListTile(
              title: Text('${data.nameOf(t.fromId)} → ${data.nameOf(t.toId)}'),
              subtitle: Text(money(t.amountMinor), style: text.titleMedium),
              trailing: FilledButton(
                onPressed: () => repo.addSettlement(
                    data.trip.id, t.fromId, t.toId, t.amountMinor),
                child: const Text('已轉帳'),
              ),
            ),
          ),
        if (data.settlements.isNotEmpty) ...[
          const _Heading('已轉帳紀錄'),
          for (final s in data.settlements)
            Card(
              child: ListTile(
                title: Text('${data.nameOf(s.fromId)} → ${data.nameOf(s.toId)}'),
                subtitle: Text(money(s.amountMinor)),
                trailing: IconButton(
                  tooltip: '取消這筆',
                  icon: const Icon(Icons.undo),
                  onPressed: () => repo.deleteSettlement(data.trip.id, s.id),
                ),
              ),
            ),
        ],
      ],
    );
  }
}

class _Heading extends StatelessWidget {
  const _Heading(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
        child: Text(text, style: Theme.of(context).textTheme.titleMedium),
      );
}
